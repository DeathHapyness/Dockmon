"""One-time Spotify login from the PC browser; sends the refresh token to the phone over adb."""
import base64
import hashlib
import http.server
import json
import secrets
import subprocess
import sys
import urllib.parse
import urllib.request

CLIENT_ID = "fd9157f81f6f4468b6e5b6875418abf5"
REDIRECT = "http://127.0.0.1:8888/callback"
SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state"
ADB = sys.argv[1] if len(sys.argv) > 1 else "adb"

verifier = secrets.token_urlsafe(48)[:64]
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
url = "https://accounts.spotify.com/authorize?" + urllib.parse.urlencode({
    "response_type": "code", "client_id": CLIENT_ID, "redirect_uri": REDIRECT,
    "code_challenge_method": "S256", "code_challenge": challenge, "scope": SCOPES,
})
print("Abra no navegador:\n" + url, flush=True)

code = None


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        global code
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        code = query.get("code", [None])[0]
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write("Pronto, pode fechar esta aba.".encode() if code else "Falhou: sem codigo.".encode())

    def log_message(self, *args):
        pass


http.server.HTTPServer(("127.0.0.1", 8888), Handler).handle_request()
if not code:
    sys.exit("login cancelado")

body = urllib.parse.urlencode({
    "grant_type": "authorization_code", "code": code, "redirect_uri": REDIRECT,
    "client_id": CLIENT_ID, "code_verifier": verifier,
}).encode()
req = urllib.request.Request("https://accounts.spotify.com/api/token", body,
                             {"Content-Type": "application/x-www-form-urlencoded"})
token = json.load(urllib.request.urlopen(req))["refresh_token"]
subprocess.run([ADB, "shell", "am", "start", "-n", "dev.rique.guardiao/.MainActivity",
                "--es", "spotifyRefreshToken", token], check=True, capture_output=True)
print("Token enviado ao celular.")
