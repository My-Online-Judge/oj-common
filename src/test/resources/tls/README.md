# TEST ONLY — never deployed

Throwaway certificates for unit tests (RSA-2048, CA 3650 days, leaves 365 days, unencrypted PKCS#8 keys, SAN DNS:<name>,DNS:localhost,IP:127.0.0.1, EKU serverAuth+clientAuth). CA private keys were deleted after signing. `rogue-client` is signed by `rogue-ca` but has CN=api-gateway; `no-cn` has subject `/O=x` only. Regenerate in this directory with:

```bash
set -euo pipefail
ca() { # name subject
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$1.key.tmp"
  openssl req -new -x509 -key "$1.key.tmp" -subj "$2" -days 3650 -sha256 \
    -addext "basicConstraints=critical,CA:true,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" -out "$1.crt"
}
leaf() { # name ca-name subject san
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$1.key"
  openssl req -new -key "$1.key" -subj "$3" -out "$1.csr"
  printf 'subjectAltName=%s\nextendedKeyUsage=serverAuth,clientAuth\n' "$4" > "$1.ext"
  openssl x509 -req -in "$1.csr" -CA "$2.crt" -CAkey "$2.key.tmp" -CAcreateserial -days 365 -sha256 -extfile "$1.ext" -out "$1.crt"
  rm "$1.csr" "$1.ext"
}
ca ca "/CN=oj-test-ca"
ca rogue-ca "/CN=rogue-test-ca"
leaf server ca "/CN=localhost" "DNS:localhost,IP:127.0.0.1"
for n in api-gateway submission-service edge tls-probe; do
  leaf $n ca "/CN=$n" "DNS:$n,DNS:localhost,IP:127.0.0.1"
done
leaf rogue-client rogue-ca "/CN=api-gateway" "DNS:api-gateway,DNS:localhost,IP:127.0.0.1"
leaf no-cn ca "/O=x" "DNS:localhost"
rm -f ca.key.tmp rogue-ca.key.tmp *.srl
```
