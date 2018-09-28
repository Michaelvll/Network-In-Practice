#!/usr/bin/env python3
# import socket module
import socket
serverSocket = socket.socket(
    family=socket.AF_INET,
    type=socket.SOCK_STREAM,
)
# Prepare a sever socket
#Fill in start
host = ''
port = 60000
serverSocket.bind((host, port))
serverSocket.listen(1)
#Fill in end
while True:
    # Establish the connection
    print('Ready to serve...')
    connectionSocket, addr = serverSocket.accept()  # Fill in start #Fill in end
    try:
        message = connectionSocket.recv(1024)  # Fill in start #Fill in end
        filename = message.split()[1]
        f = open(filename[1:])
        outputdata = [line.encode() for line in f.readlines()]
        f.close()  # Fill in start #Fill in end
        # Send one HTTP header line into socket
        #Fill in start
        connectionSocket.send(b'HTTP/1.1 200 OK\r\n')
        #Fill in end
        # Send the content of the requested file to the client
        for i in range(0, len(outputdata)):
            connectionSocket.send(outputdata[i])
        connectionSocket.close()
    except IOError:
        pass
        # Send response message for file not found
        #Fill in start
        connectionSocket.send(b'HTTP/1.1 404 Not Found')
        #Fill in end
        # Close client socket
        #Fill in start
        connectionSocket.close()
        #Fill in end


serverSocket.close()
