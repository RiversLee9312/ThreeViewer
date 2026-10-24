"""Optional localhost bridge for environments where JVM HTTPS transfers stall.
All artifacts come from the original official repositories; no mirror is used.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.request import urlopen, Request
from urllib.error import HTTPError
import hashlib, threading, time
ROOT=Path(__file__).resolve().parents[1]/'.tools/maven-cache'
ROOT.mkdir(parents=True,exist_ok=True)
BASE={'google':'https://dl.google.com/dl/android/maven2/', 'central':'https://repo.maven.apache.org/maven2/', 'plugins':'https://plugins.gradle.org/m2/'}
locks={}; guard=threading.Lock()
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def do_HEAD(self): self.serve(False)
    def do_GET(self): self.serve(True)
    def serve(self,body):
        try:
            origin,path=self.path.strip('/').split('/',1)
            if origin not in BASE or '..' in path: self.send_error(400); return
            key=hashlib.sha256(self.path.encode()).hexdigest()
            dest=ROOT/key
            if not body and not dest.exists():
                with urlopen(Request(BASE[origin]+path,method='HEAD'),timeout=30) as r:
                    self.send_response(200)
                    self.send_header('Content-Length',r.headers.get('Content-Length','0'))
                    self.end_headers()
                return
            with guard: lock=locks.setdefault(key,threading.Lock())
            with lock:
                if not dest.exists():
                    for attempt in range(3):
                        try:
                            with urlopen(Request(BASE[origin]+path,headers={'User-Agent':'ThreeViewer-build/1.0'}),timeout=30) as r:
                                self.send_response(200)
                                length=r.headers.get('Content-Length')
                                if length: self.send_header('Content-Length',length)
                                self.end_headers()
                                tmp=dest.with_suffix('.partial')
                                send=body
                                with tmp.open('wb') as f:
                                    while chunk:=r.read(65536):
                                        f.write(chunk)
                                        if send:
                                            try: self.wfile.write(chunk)
                                            except OSError: send=False
                                tmp.replace(dest)
                            if dest.stat().st_size>1000000: print(path,dest.stat().st_size,flush=True)
                            return
                        except HTTPError as e:
                            self.send_error(e.code); return
                        except Exception:
                            if attempt==2: raise
                            time.sleep(.5)
            self.send_response(200)
            self.send_header('Content-Length',str(dest.stat().st_size)); self.end_headers()
            if body:
                with dest.open('rb') as f:
                    while chunk:=f.read(1024*1024): self.wfile.write(chunk)
        except HTTPError as e:
            self.send_error(e.code)
        except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError): pass
        except Exception as e:
            print(type(e).__name__,str(e),flush=True)
            self.send_error(502)
print('Official Maven bridge: http://127.0.0.1:18765',flush=True)
ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()
