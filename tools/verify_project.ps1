$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 "$root\tools\verify_project.py"
    & py -3 "$root\tools\inspect_tflite.py" --verify-sr-contract
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python "$root\tools\verify_project.py"
    & python "$root\tools\inspect_tflite.py" --verify-sr-contract
} else {
    throw "Python 3 was not found. Install Python or add it to PATH."
}
