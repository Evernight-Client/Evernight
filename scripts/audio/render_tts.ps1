# Renders the killstreak announcer lines with the Windows speech synthesiser.
# The output is generated audio owned by the project: no third-party recordings are involved.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/audio/render_tts.ps1

param([string]$OutDir)

Add-Type -AssemblyName System.Speech

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $root 'build\audio-tts' }
$out = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $out | Out-Null

$lines = @(
    @{ f = 'doublekill_2/doublekill';        t = 'Double kill' },
    @{ f = 'triplekill_3/triplekill';        t = 'Triple kill' },
    @{ f = 'triplekill_3/multikill';         t = 'Multi kill' },
    @{ f = 'dominating_4/dominating';        t = 'Dominating' },
    @{ f = 'dominating_4/killingspree';      t = 'Killing spree' },
    @{ f = 'dominating_4/headhunter';        t = 'Head hunter' },
    @{ f = 'megakill_5/megakill';            t = 'Mega kill' },
    @{ f = 'megakill_5/impressive';          t = 'Impressive' },
    @{ f = 'megakill_5/humiliation';         t = 'Humiliation' },
    @{ f = 'unstoppable_6/unstoppable';      t = 'Unstoppable' },
    @{ f = 'unstoppable_6/rampage';          t = 'Rampage' },
    @{ f = 'wickedsick_7/wickedsick';        t = 'Wicked sick' },
    @{ f = 'wickedsick_7/ultrakill';         t = 'Ultra kill' },
    @{ f = 'wickedsick_7/ludicrouskill';     t = 'Ludicrous kill' },
    @{ f = 'monsterkill_8up/monsterkill';    t = 'Monster kill' },
    @{ f = 'monsterkill_8up/godlike';        t = 'Godlike' },
    @{ f = 'monsterkill_8up/perfect';        t = 'Perfect' }
)

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$english = $synth.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo } | Where-Object { $_.Culture.Name -like 'en*' } | Select-Object -First 1
if (-not $english) { throw 'No English speech voice is installed' }
$synth.SelectVoice($english.Name)
$synth.Rate = 1
$synth.Volume = 100

$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    48000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($line in $lines) {
    $target = Join-Path $out (($line.f -replace '/', '_') + '.wav')
    $synth.SetOutputToWaveFile($target, $format)
    $synth.Speak($line.t)
    $synth.SetOutputToNull()
    Write-Output ("{0} -> {1}" -f $line.t, $target)
}

$synth.Dispose()
