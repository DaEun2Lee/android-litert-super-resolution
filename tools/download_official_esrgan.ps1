$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "download_official_esrgan.py"

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 $script
    exit $LASTEXITCODE
}

if (Get-Command python -ErrorAction SilentlyContinue) {
    & python $script
    exit $LASTEXITCODE
}

throw "Python 3 was not found. Install Python or add it to PATH."
