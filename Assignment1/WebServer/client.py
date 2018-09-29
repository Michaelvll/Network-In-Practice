#!usr/bin/env python3

import sys
import socket


if __name__ == "__main__":
    assert len(sys.argv) == 4

    _, server_host, server_port, filename = sys.argv

    clientSocket = socket.socket()
    clientSocket.connect((server_host, int(server_port)))

    clientSocket.send(('GET /' + filename).encode())

    while True:
        data = clientSocket.recv(1024)
        print(data)
        if not data:
            break
    clientSocket.close()
