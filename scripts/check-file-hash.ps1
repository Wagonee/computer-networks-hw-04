$cid = (Select-String -Path client-config.properties -Pattern '^clientId=').Line.Split('=')[1]
foreach ($f in Get-ChildItem client-data) {
    $c = (Get-FileHash $f.FullName -Algorithm SHA256).Hash
    $s = (Get-FileHash "server-storage\$cid\$($f.Name)" -Algorithm SHA256).Hash
    "{0}: client={1}  server={2}  match={3}" -f $f.Name, $c.Substring(0,12), $s.Substring(0,12), ($c -eq $s)
}