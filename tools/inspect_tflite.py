#!/usr/bin/env python3
"""Dependency-free TFLite FlatBuffer summary for this project's model contract."""
from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path

TENSOR_TYPES = {
    0: "FLOAT32",
    1: "FLOAT16",
    2: "INT32",
    3: "UINT8",
    4: "INT64",
    6: "BOOL",
    7: "INT16",
    9: "INT8",
    10: "FLOAT64",
    16: "UINT32",
    17: "UINT16",
}


class Table:
    def __init__(self, data: bytes, position: int) -> None:
        self.data = data
        self.position = position
        distance = struct.unpack_from("<i", data, position)[0]
        self.vtable = position - distance
        self.vtable_size = struct.unpack_from("<H", data, self.vtable)[0]

    def field_position(self, slot: int) -> int | None:
        entry = self.vtable + 4 + slot * 2
        if entry + 2 > self.vtable + self.vtable_size:
            return None
        offset = struct.unpack_from("<H", self.data, entry)[0]
        return self.position + offset if offset else None

    def uint8(self, slot: int, default: int = 0) -> int:
        position = self.field_position(slot)
        return self.data[position] if position is not None else default

    def uint32(self, slot: int, default: int = 0) -> int:
        position = self.field_position(slot)
        return struct.unpack_from("<I", self.data, position)[0] if position else default

    def target(self, slot: int) -> int | None:
        position = self.field_position(slot)
        if position is None:
            return None
        return position + struct.unpack_from("<I", self.data, position)[0]

    def string(self, slot: int) -> str | None:
        target = self.target(slot)
        if target is None:
            return None
        length = struct.unpack_from("<I", self.data, target)[0]
        return self.data[target + 4 : target + 4 + length].decode("utf-8", "replace")

    def vector_int32(self, slot: int) -> list[int]:
        target = self.target(slot)
        if target is None:
            return []
        count = struct.unpack_from("<I", self.data, target)[0]
        if count == 0:
            return []
        return list(struct.unpack_from(f"<{count}i", self.data, target + 4))

    def vector_tables(self, slot: int) -> list["Table"]:
        target = self.target(slot)
        if target is None:
            return []
        count = struct.unpack_from("<I", self.data, target)[0]
        result: list[Table] = []
        for index in range(count):
            element = target + 4 + index * 4
            table_position = element + struct.unpack_from("<I", self.data, element)[0]
            result.append(Table(self.data, table_position))
        return result


def tensor_summary(tensor: Table, index: int) -> dict[str, object]:
    tensor_type = tensor.uint8(1)
    return {
        "index": index,
        "name": tensor.string(3),
        "shape": tensor.vector_int32(0),
        "type": TENSOR_TYPES.get(tensor_type, f"TYPE_{tensor_type}"),
        "buffer": tensor.uint32(2),
    }


def inspect(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    if len(data) < 32 or data[4:8] != b"TFL3":
        raise ValueError("Not a TFLite FlatBuffer (missing TFL3 identifier).")

    root_position = struct.unpack_from("<I", data, 0)[0]
    model = Table(data, root_position)
    subgraphs = model.vector_tables(2)
    if not subgraphs:
        raise ValueError("Model contains no subgraphs.")

    graph = subgraphs[0]
    tensors = graph.vector_tables(0)
    input_indices = graph.vector_int32(1)
    output_indices = graph.vector_int32(2)
    inputs = [tensor_summary(tensors[index], index) for index in input_indices]
    outputs = [tensor_summary(tensors[index], index) for index in output_indices]

    return {
        "path": str(path),
        "bytes": len(data),
        "schema_version": model.uint32(0),
        "description": model.string(3),
        "subgraph_count": len(subgraphs),
        "operator_code_count": len(model.vector_tables(1)),
        "operator_count_first_subgraph": len(graph.vector_tables(3)),
        "inputs": inputs,
        "outputs": outputs,
    }


def verify_sr_contract(summary: dict[str, object]) -> None:
    inputs = summary["inputs"]
    outputs = summary["outputs"]
    if not isinstance(inputs, list) or not isinstance(outputs, list):
        raise ValueError("Invalid inspection result.")
    if len(inputs) != 1 or len(outputs) != 1:
        raise ValueError("Expected exactly one input and one output.")

    input_tensor = inputs[0]
    output_tensor = outputs[0]
    if input_tensor["shape"] != [1, 50, 50, 3] or input_tensor["type"] != "FLOAT32":
        raise ValueError(f"Unexpected input contract: {input_tensor}")
    if output_tensor["shape"] != [1, 200, 200, 3] or output_tensor["type"] != "FLOAT32":
        raise ValueError(f"Unexpected output contract: {output_tensor}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "model",
        nargs="?",
        default=str(
            Path(__file__).resolve().parent.parent
            / "app"
            / "src"
            / "main"
            / "assets"
            / "sr_x4.tflite"
        ),
    )
    parser.add_argument("--verify-sr-contract", action="store_true")
    args = parser.parse_args()

    summary = inspect(Path(args.model))
    if args.verify_sr_contract:
        verify_sr_contract(summary)
        summary["sr_contract"] = "verified"
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
