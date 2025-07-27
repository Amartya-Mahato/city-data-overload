# PowerShell script to remove Lombok annotations and add necessary constructors/loggers

$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    
    # Remove Lombok imports
    $content = $content -replace "import lombok\.[^;]+;`n?", ""
    
    # Remove Lombok annotations (with optional parameters)
    $content = $content -replace "@RequiredArgsConstructor(\([^)]*\))?`n?", ""
    $content = $content -replace "@AllArgsConstructor(\([^)]*\))?`n?", ""
    $content = $content -replace "@NoArgsConstructor(\([^)]*\))?`n?", ""
    $content = $content -replace "@Data(\([^)]*\))?`n?", ""
    $content = $content -replace "@Builder(\([^)]*\))?`n?", ""
    $content = $content -replace "@Getter(\([^)]*\))?`n?", ""
    $content = $content -replace "@Setter(\([^)]*\))?`n?", ""
    $content = $content -replace "@Slf4j(\([^)]*\))?`n?", ""
    $content = $content -replace "@ToString(\([^)]*\))?`n?", ""
    $content = $content -replace "@EqualsAndHashCode(\([^)]*\))?`n?", ""
    
    # Clean up multiple empty lines
    $content = $content -replace "`n`n`n+", "`n`n"
    
    # Only write if content changed
    if ($content -ne $originalContent) {
        Write-Host "Processing: $($file.FullName)"
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "Lombok annotations removed from all Java files."
