$java = 'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin\java.exe'
$classes = 'C:\Users\hp\Downloads\BRO\NovaBrowser\browser-core\build\intermediates\runtime_library_classes_dir\debug\bundleLibRuntimeToDirDebug'
$std = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches" -Filter 'kotlin-stdlib-2.*.jar' -Recurse | Select-Object -ExpandProperty FullName -First 1

$cp = "$classes;$std"
& $java -cp $cp com.gintama.novabrowser.core.security.SecurityVerificationRunner
