from socket import *
import os
import sys
# Create a server socket, bind it to a port and start listening
tcpSerSock = socket(AF_INET, SOCK_STREAM)
# Fill in start.
tcpSerSock.bind(('', 6060))
tcpSerSock.listen(1)
# Fill in end.
while 1:
    # Strat receiving data from the client
    print('Ready to serve...')
    tcpCliSock, addr = tcpSerSock.accept()
    print('Received a connection from:', addr)
    message = tcpCliSock.recv(1024).decode()  # Fill in start. # Fill in end.
    print("Get message: ", [message])
    # Extract the filename from the given message

    url = message.split()[1].partition("/")[2]
    print("URL:", url)
    filename = url.replace("/", ' ')
    fileExist = "false"
    filetouse = os.path.join('cache', filename)
    print("Save to:", filetouse)
    try:
        # Check wether the file exist in the cache
        f = open(filetouse, "r")
        outputdata = f.readlines()
        fileExist = "true"
        # ProxyServer finds a cache hit and generates a response message
        tcpCliSock.send(b"HTTP/1.0 200 OK\r\n")

        tcpCliSock.send("Content-Type:text/html; charset=UTF-8\r\n")
        # Fill in start.
        for content in outputdata:
            tcpCliSock.send(content.encode())
        # Fill in end.
        print('Read from cache')
        # Error handling for file not found in cache
    except IOError:
        if fileExist == "false":
            # Create a socket on the proxyserver
            c = socket(AF_INET, SOCK_STREAM)
            hostn = url.replace("www.", "", 1).split('/')[0]
            try:
                # Connect to the socket to port 80
                # Fill in start.
                c.connect((hostn, 80))
                # Fill in end.
                # Create a temporary file on this socket and ask port 80 for the file requested by the client
                getmsg = ("GET /" + url.partition('/')
                          [2] + " HTTP/1.0\r\n\r\n").encode()
                print("Send:", getmsg)
                c.send(getmsg)
                # Read the response into buffer
                # Fill in start.
                outputBuffer = b''
                while True:
                    buf = c.recv(1024)
                    outputBuffer += buf
                    if not buf:
                        break
                print(outputBuffer)
                # Fill in end.
                # Create a new file in the cache for the requested file.
                # Also send the response in the buffer to client socket and the corresponding file in the cache
                # tmpFile = open(filetouse, "wb")
                # # Fill in start.

                # tmpFile.write(outputBuffer)
                tcpCliSock.send(outputBuffer)
                c.close()
                # tmpFile.close()
                # Fill in end.
            except Exception as e:
                print(str(e))
                print("Illegal request")
        else:
            # HTTP response message for file not found
            # Fill in start.
            # Fill in end.
            # Close the client and the server sockets
            pass
    tcpCliSock.close()
# Fill in start.
tcpSerSock.close()
# Fill in end.
