#!/usr/bin/env python3
"""Generate a tiny, dependency-free x4 TFLite demo model.

Graph: FLOAT32 NHWC [1,50,50,3] -> Conv2D(1x1, 48 channels) ->
DepthToSpace(block=4) -> FLOAT32 NHWC [1,200,200,3].

The fixed 1x1 weights replicate RGB values into the 16 sub-pixel locations.
It is intentionally a small deployment/demo network, not a trained ESRGAN.
"""
from __future__ import annotations

import argparse
import struct
from pathlib import Path
from typing import Iterable, Sequence


class FlatBufferBuilder:
    """Small subset of the FlatBuffers reverse builder used by this model."""

    def __init__(self, initial_size: int = 4096) -> None:
        self.buf = bytearray(initial_size)
        self.head = initial_size
        self.minalign = 1
        self.current_vtable: list[int] | None = None
        self.object_end = 0
        self.nested = False
        self.vector_num_elems: int | None = None
        self.finished = False

    def offset(self) -> int:
        return len(self.buf) - self.head

    def grow(self) -> None:
        old = self.buf
        new_size = max(1, len(old) * 2)
        self.buf = bytearray(new_size)
        self.buf[new_size - len(old):] = old
        self.head += new_size - len(old)

    def pad(self, count: int) -> None:
        if count <= 0:
            return
        self.head -= count
        self.buf[self.head:self.head + count] = b"\x00" * count

    def prep(self, size: int, additional_bytes: int) -> None:
        self.minalign = max(self.minalign, size)
        align = (~(len(self.buf) - self.head + additional_bytes) + 1) & (size - 1)
        needed = align + size + additional_bytes
        while self.head < needed:
            self.grow()
        self.pad(align)

    def _place(self, fmt: str, value: int | float) -> None:
        size = struct.calcsize(fmt)
        self.head -= size
        struct.pack_into(fmt, self.buf, self.head, value)

    def prepend_uint8(self, value: int) -> None:
        self.prep(1, 0)
        self._place("<B", value)

    def prepend_int8(self, value: int) -> None:
        self.prep(1, 0)
        self._place("<b", value)

    def prepend_uint16(self, value: int) -> None:
        self.prep(2, 0)
        self._place("<H", value)

    def prepend_uint32(self, value: int) -> None:
        self.prep(4, 0)
        self._place("<I", value)

    def prepend_int32(self, value: int) -> None:
        self.prep(4, 0)
        self._place("<i", value)

    def prepend_uint64(self, value: int) -> None:
        self.prep(8, 0)
        self._place("<Q", value)

    def prepend_float32(self, value: float) -> None:
        self.prep(4, 0)
        self._place("<f", value)

    def prepend_uoffset_relative(self, target_offset: int) -> None:
        self.prep(4, 0)
        if target_offset > self.offset():
            raise ValueError("FlatBuffer offset points to an object not yet written")
        relative = self.offset() - target_offset + 4
        self._place("<I", relative)

    def start_vector(self, elem_size: int, count: int, alignment: int) -> None:
        if self.nested:
            raise RuntimeError("Cannot start vector while nested")
        self.nested = True
        self.vector_num_elems = count
        self.prep(4, elem_size * count)
        self.prep(alignment, elem_size * count)

    def end_vector(self) -> int:
        if not self.nested or self.vector_num_elems is None:
            raise RuntimeError("No vector is being built")
        self.nested = False
        # Space/alignment for the length was reserved by start_vector().
        self._place("<I", self.vector_num_elems)
        self.vector_num_elems = None
        return self.offset()

    def create_int32_vector(self, values: Sequence[int]) -> int:
        self.start_vector(4, len(values), 4)
        for value in reversed(values):
            self.prepend_int32(value)
        return self.end_vector()

    def create_offsets_vector(self, offsets: Sequence[int]) -> int:
        self.start_vector(4, len(offsets), 4)
        for offset in reversed(offsets):
            self.prepend_uoffset_relative(offset)
        return self.end_vector()

    def create_byte_vector(self, data: bytes, alignment: int = 1) -> int:
        if self.nested:
            raise RuntimeError("Cannot start vector while nested")
        self.nested = True
        self.vector_num_elems = len(data)
        self.prep(4, len(data))
        self.prep(alignment, len(data))
        self.head -= len(data)
        self.buf[self.head:self.head + len(data)] = data
        return self.end_vector()

    def create_string(self, value: str) -> int:
        data = value.encode("utf-8")
        if self.nested:
            raise RuntimeError("Cannot create string while nested")
        self.nested = True
        self.vector_num_elems = len(data)
        self.prep(4, len(data) + 1)
        self._place("<B", 0)
        self.head -= len(data)
        self.buf[self.head:self.head + len(data)] = data
        return self.end_vector()

    def start_object(self, field_count: int) -> None:
        if self.nested:
            raise RuntimeError("Cannot start object while nested")
        self.current_vtable = [0] * field_count
        self.object_end = self.offset()
        self.nested = True

    def slot(self, index: int) -> None:
        if self.current_vtable is None:
            raise RuntimeError("No object is being built")
        self.current_vtable[index] = self.offset()

    def add_uint8(self, slot: int, value: int, default: int = 0) -> None:
        if value != default:
            self.prepend_uint8(value)
            self.slot(slot)

    def add_int8(self, slot: int, value: int, default: int = 0) -> None:
        if value != default:
            self.prepend_int8(value)
            self.slot(slot)

    def add_uint32(self, slot: int, value: int, default: int = 0) -> None:
        if value != default:
            self.prepend_uint32(value)
            self.slot(slot)

    def add_int32(self, slot: int, value: int, default: int = 0) -> None:
        if value != default:
            self.prepend_int32(value)
            self.slot(slot)

    def add_uint64(self, slot: int, value: int, default: int = 0) -> None:
        if value != default:
            self.prepend_uint64(value)
            self.slot(slot)

    def add_offset(self, slot: int, value: int) -> None:
        if value:
            self.prepend_uoffset_relative(value)
            self.slot(slot)

    def end_object(self) -> int:
        if not self.nested or self.current_vtable is None:
            raise RuntimeError("No object is being built")
        fields = self.current_vtable
        self.nested = False

        # Placeholder for the signed back-offset from object to vtable.
        self.prepend_int32(0)
        object_offset = self.offset()

        # Emit all entries to keep stable slot indices.
        for field_offset in reversed(fields):
            relative = object_offset - field_offset if field_offset else 0
            if relative > 0xFFFF:
                raise ValueError("Object field offset exceeds uint16")
            self.prepend_uint16(relative)

        object_size = object_offset - self.object_end
        vtable_size = (len(fields) + 2) * 2
        self.prepend_uint16(object_size)
        self.prepend_uint16(vtable_size)

        vtable_distance = self.offset() - object_offset
        object_absolute = len(self.buf) - object_offset
        struct.pack_into("<i", self.buf, object_absolute, vtable_distance)

        self.current_vtable = None
        return object_offset

    def finish(self, root_offset: int, file_identifier: bytes = b"TFL3") -> bytes:
        if len(file_identifier) != 4:
            raise ValueError("FlatBuffer file identifier must be exactly 4 bytes")
        prep_size = 4 + len(file_identifier)
        self.prep(self.minalign, prep_size)
        self.prep(4, len(file_identifier))
        for value in reversed(file_identifier):
            self._place("<B", value)
        self.prepend_uoffset_relative(root_offset)
        self.finished = True
        return bytes(self.buf[self.head:])


# TFLite schema constants used here.
TENSOR_FLOAT32 = 0
BUILTIN_CONV_2D = 3
BUILTIN_DEPTH_TO_SPACE = 5
BUILTIN_OPTIONS_CONV_2D = 1
BUILTIN_OPTIONS_DEPTH_TO_SPACE = 94
PADDING_SAME = 0
ACTIVATION_NONE = 0


def create_tensor(builder: FlatBufferBuilder, shape: Sequence[int], buffer_index: int, name: str) -> int:
    shape_offset = builder.create_int32_vector(shape)
    name_offset = builder.create_string(name)
    builder.start_object(11)
    builder.add_offset(0, shape_offset)
    builder.add_int8(1, TENSOR_FLOAT32, 0)
    builder.add_uint32(2, buffer_index, 0)
    builder.add_offset(3, name_offset)
    builder.add_uint8(8, 1, 0)  # has_rank = true
    return builder.end_object()


def create_buffer(builder: FlatBufferBuilder, data: bytes | None) -> int:
    data_offset = builder.create_byte_vector(data, alignment=16) if data else 0
    builder.start_object(3)
    builder.add_offset(0, data_offset)
    return builder.end_object()


def create_operator_code(builder: FlatBufferBuilder, builtin_code: int, version: int = 1) -> int:
    builder.start_object(4)
    builder.add_int8(0, builtin_code, 0)
    builder.add_int32(2, version, 1)
    builder.add_int32(3, builtin_code, 0)
    return builder.end_object()


def create_conv_options(builder: FlatBufferBuilder) -> int:
    builder.start_object(7)
    builder.add_int8(0, PADDING_SAME, 0)
    builder.add_int32(1, 1, 0)  # stride_w
    builder.add_int32(2, 1, 0)  # stride_h
    builder.add_int8(3, ACTIVATION_NONE, 0)
    builder.add_int32(4, 1, 1)
    builder.add_int32(5, 1, 1)
    return builder.end_object()


def create_depth_to_space_options(builder: FlatBufferBuilder) -> int:
    builder.start_object(1)
    builder.add_int32(0, 4, 0)
    return builder.end_object()


def create_operator(
    builder: FlatBufferBuilder,
    opcode_index: int,
    inputs: Sequence[int],
    outputs: Sequence[int],
    builtin_options_type: int,
    builtin_options: int,
) -> int:
    inputs_offset = builder.create_int32_vector(inputs)
    outputs_offset = builder.create_int32_vector(outputs)
    builder.start_object(14)
    builder.add_uint32(0, opcode_index, 0)
    builder.add_offset(1, inputs_offset)
    builder.add_offset(2, outputs_offset)
    builder.add_uint8(3, builtin_options_type, 0)
    builder.add_offset(4, builtin_options)
    builder.add_int32(13, -1, -1)
    return builder.end_object()


def model_weights() -> tuple[bytes, bytes]:
    # Conv2D weights are OHWI: [48, 1, 1, 3]. Channels are ordered as
    # (subpixel_index * RGB + color_channel) for DEPTH_TO_SPACE(block=4).
    weights: list[float] = []
    for output_channel in range(48):
        source_channel = output_channel % 3
        for input_channel in range(3):
            weights.append(1.0 if input_channel == source_channel else 0.0)
    biases = [0.0] * 48
    return (
        struct.pack(f"<{len(weights)}f", *weights),
        struct.pack(f"<{len(biases)}f", *biases),
    )


def build_model() -> bytes:
    builder = FlatBufferBuilder()
    weight_bytes, bias_bytes = model_weights()

    empty_buffer = create_buffer(builder, None)
    weight_buffer = create_buffer(builder, weight_bytes)
    bias_buffer = create_buffer(builder, bias_bytes)

    tensors = [
        create_tensor(builder, [1, 50, 50, 3], 0, "input_rgb_0_255"),
        create_tensor(builder, [48, 1, 1, 3], 1, "replication_weights"),
        create_tensor(builder, [48], 2, "replication_bias"),
        create_tensor(builder, [1, 50, 50, 48], 0, "subpixel_features"),
        create_tensor(builder, [1, 200, 200, 3], 0, "output_rgb_0_255"),
    ]

    conv_options = create_conv_options(builder)
    depth_options = create_depth_to_space_options(builder)
    operators = [
        create_operator(builder, 0, [0, 1, 2], [3], BUILTIN_OPTIONS_CONV_2D, conv_options),
        create_operator(builder, 1, [3], [4], BUILTIN_OPTIONS_DEPTH_TO_SPACE, depth_options),
    ]

    tensors_vector = builder.create_offsets_vector(tensors)
    inputs_vector = builder.create_int32_vector([0])
    outputs_vector = builder.create_int32_vector([4])
    operators_vector = builder.create_offsets_vector(operators)
    subgraph_name = builder.create_string("main")
    builder.start_object(6)
    builder.add_offset(0, tensors_vector)
    builder.add_offset(1, inputs_vector)
    builder.add_offset(2, outputs_vector)
    builder.add_offset(3, operators_vector)
    builder.add_offset(4, subgraph_name)
    builder.add_int32(5, -1, -1)
    subgraph = builder.end_object()

    op_codes = [
        create_operator_code(builder, BUILTIN_CONV_2D),
        create_operator_code(builder, BUILTIN_DEPTH_TO_SPACE),
    ]
    op_codes_vector = builder.create_offsets_vector(op_codes)
    subgraphs_vector = builder.create_offsets_vector([subgraph])
    description = builder.create_string(
        "Self-contained x4 deployment demo: 1x1 Conv2D + DepthToSpace; replace with ESRGAN for perceptual SR"
    )
    buffers_vector = builder.create_offsets_vector([empty_buffer, weight_buffer, bias_buffer])

    builder.start_object(10)
    builder.add_uint32(0, 3, 0)
    builder.add_offset(1, op_codes_vector)
    builder.add_offset(2, subgraphs_vector)
    builder.add_offset(3, description)
    builder.add_offset(4, buffers_vector)
    model = builder.end_object()
    return builder.finish(model, b"TFL3")


class TableReader:
    def __init__(self, data: bytes, table_pos: int) -> None:
        self.data = data
        self.pos = table_pos
        self.vtable = table_pos - struct.unpack_from("<i", data, table_pos)[0]
        self.vtable_size = struct.unpack_from("<H", data, self.vtable)[0]

    def field_pos(self, slot: int) -> int | None:
        entry = self.vtable + 4 + slot * 2
        if entry + 2 > self.vtable + self.vtable_size:
            return None
        offset = struct.unpack_from("<H", self.data, entry)[0]
        return self.pos + offset if offset else None

    def u8(self, slot: int, default: int = 0) -> int:
        pos = self.field_pos(slot)
        return struct.unpack_from("<B", self.data, pos)[0] if pos is not None else default

    def i8(self, slot: int, default: int = 0) -> int:
        pos = self.field_pos(slot)
        return struct.unpack_from("<b", self.data, pos)[0] if pos is not None else default

    def u32(self, slot: int, default: int = 0) -> int:
        pos = self.field_pos(slot)
        return struct.unpack_from("<I", self.data, pos)[0] if pos is not None else default

    def i32(self, slot: int, default: int = 0) -> int:
        pos = self.field_pos(slot)
        return struct.unpack_from("<i", self.data, pos)[0] if pos is not None else default

    def target(self, slot: int) -> int | None:
        pos = self.field_pos(slot)
        return pos + struct.unpack_from("<I", self.data, pos)[0] if pos is not None else None

    def string(self, slot: int) -> str | None:
        target = self.target(slot)
        if target is None:
            return None
        length = struct.unpack_from("<I", self.data, target)[0]
        return self.data[target + 4:target + 4 + length].decode("utf-8")

    def vector_len(self, slot: int) -> int:
        target = self.target(slot)
        return struct.unpack_from("<I", self.data, target)[0] if target is not None else 0

    def vector_i32(self, slot: int) -> list[int]:
        target = self.target(slot)
        if target is None:
            return []
        count = struct.unpack_from("<I", self.data, target)[0]
        return list(struct.unpack_from(f"<{count}i", self.data, target + 4))

    def vector_tables(self, slot: int) -> list["TableReader"]:
        target = self.target(slot)
        if target is None:
            return []
        count = struct.unpack_from("<I", self.data, target)[0]
        result = []
        for index in range(count):
            element = target + 4 + index * 4
            table_pos = element + struct.unpack_from("<I", self.data, element)[0]
            result.append(TableReader(self.data, table_pos))
        return result

    def vector_bytes(self, slot: int) -> bytes:
        target = self.target(slot)
        if target is None:
            return b""
        count = struct.unpack_from("<I", self.data, target)[0]
        return self.data[target + 4:target + 4 + count]


def verify_model(data: bytes) -> dict[str, object]:
    if len(data) < 32 or data[4:8] != b"TFL3":
        raise ValueError("Not a TFLite FlatBuffer (missing TFL3 identifier)")
    root = struct.unpack_from("<I", data, 0)[0]
    model = TableReader(data, root)
    if model.u32(0) != 3:
        raise ValueError("Unexpected TFLite schema version")

    op_codes = model.vector_tables(1)
    subgraphs = model.vector_tables(2)
    buffers = model.vector_tables(4)
    if len(op_codes) != 2 or len(subgraphs) != 1 or len(buffers) != 3:
        raise ValueError("Unexpected model table counts")
    codes = [code.i32(3, code.i8(0)) for code in op_codes]
    if codes != [BUILTIN_CONV_2D, BUILTIN_DEPTH_TO_SPACE]:
        raise ValueError(f"Unexpected operator codes: {codes}")

    graph = subgraphs[0]
    tensors = graph.vector_tables(0)
    operators = graph.vector_tables(3)
    if graph.vector_i32(1) != [0] or graph.vector_i32(2) != [4]:
        raise ValueError("Unexpected graph inputs/outputs")
    if len(tensors) != 5 or len(operators) != 2:
        raise ValueError("Unexpected graph size")

    tensor_shapes = [tensor.vector_i32(0) for tensor in tensors]
    expected_shapes = [
        [1, 50, 50, 3],
        [48, 1, 1, 3],
        [48],
        [1, 50, 50, 48],
        [1, 200, 200, 3],
    ]
    if tensor_shapes != expected_shapes:
        raise ValueError(f"Unexpected tensor shapes: {tensor_shapes}")

    option_types = [operator.u8(3) for operator in operators]
    if option_types != [BUILTIN_OPTIONS_CONV_2D, BUILTIN_OPTIONS_DEPTH_TO_SPACE]:
        raise ValueError(f"Unexpected builtin option types: {option_types}")

    actual_weights = buffers[1].vector_bytes(0)
    actual_biases = buffers[2].vector_bytes(0)
    expected_weights, expected_biases = model_weights()
    weight_size = len(actual_weights)
    bias_size = len(actual_biases)
    if actual_weights != expected_weights or actual_biases != expected_biases:
        raise ValueError("Model weights do not match the deterministic RGB replication graph")

    return {
        "bytes": len(data),
        "description": model.string(3),
        "operator_codes": codes,
        "tensor_shapes": tensor_shapes,
        "weight_bytes": weight_size,
        "bias_bytes": bias_size,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", nargs="?", default="sr_x4_demo.tflite")
    args = parser.parse_args()
    data = build_model()
    info = verify_model(data)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    print(f"Wrote {output} ({len(data)} bytes)")
    print(f"Verified: {info}")


if __name__ == "__main__":
    main()
