#!/usr/bin/env python3
# import socket module
import socket
import threading


def handle_connection(connectionSocket, addr):
    try:
        message = connectionSocket.recv(1024).decode()
        if not message:
            connectionSocket.send(b'HTTP/1.1 400 Bad Request')
            connectionSocket.close()
            return
        filename = message.split()[1]
        f = open(filename[1:])
        outputdata = [line.encode() for line in f.readlines()]
        f.close()
        # Send one HTTP header line into socket
        connectionSocket.send(b'HTTP/1.1 200 OK\r\n\r\n')
        # Send the content of the requested file to the client
        for i in range(0, len(outputdata)):
            connectionSocket.send(outputdata[i])
    except IOError:
        # Send response message for file not found
        connectionSocket.send(b'HTTP/1.1 404 Not Found')
    # Close client socket
    connectionSocket.close()


if __name__ == "__main__":
    serverSocket = socket.socket(
        family=socket.AF_INET,
        type=socket.SOCK_STREAM,
    )
    # Prepare a sever socket
    # Fill in start
    host = ''
    port = 6060
    serverSocket.bind((host, port))
    serverSocket.listen(5)
    # Fill in end

    while True:
        # Establish the connection
        print('Ready to serve...')
        connectionSocket, addr = serverSocket.accept()  # Fill in start #Fill in end
        connectionSocket.settimeout(60)
        threading.Thread(target=handle_connection,
                         args=(connectionSocket, addr)).start()

    serverSocket.close()
