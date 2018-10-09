from socket import *
import os
import sys
# Create a server socket, bind it to a port and start listening
tcpSerSock = socket(AF_INET, SOCK_STREAM)
tcpSerSock.bind(('', 6060))
tcpSerSock.listen(3)
lastHost = ''
if not os.path.exists('cache'):
    os.makedirs('cache')


def readfile(fileread, tcpCliSock, lastHost, hostn):
    if os.path.isfile(fileread):
        print("fileread:", fileread)
        # Check wether the file exist in the cache
        with open(fileread, "rb") as f:
            outputdata = f.read()
            # ProxyServer finds a cache hit and generates a response message
            # tcpCliSock.send(b"HTTP/1.0 200 OK\r\n")

            # tcpCliSock.send(b"Content-Type:text/html; charset=UTF-8\r\n")
            tcpCliSock.send(outputdata)
            lastHost = hostn
            print('Read from cache')
        return True, lastHost
    return False, lastHost


while True:
    # Strat receiving data from the client
    print('Ready to serve...')
    tcpCliSock, addr = tcpSerSock.accept()
    print('Received a connection from:', addr)
    message = tcpCliSock.recv(1024).decode()  # Fill in start. # Fill in end.
    print("Get message: ", [message])
    if message == '':
        # lastHost = ''
        continue
    # Extract the filename from the given message
    url = message.split()[1].partition("//")[2]
    if 'google' in message:
        continue
    print("URL:", url)
    fileread = os.path.join('cache', url.replace(
        "www.", "", 1).replace('/', ' '))
    hostn = url.replace("www.", "", 1).split('/')[0]
    readsucc, lastHost = readfile(fileread, tcpCliSock, lastHost, hostn)
    if not readsucc:
        hostn = url.replace("www.", "", 1).split('/')[0]

        # Create a socket on the proxyserver
        c = socket(AF_INET, SOCK_STREAM)

        try:
            # Connect to the socket to port 80
            c.connect((hostn, 80))
            lastHost = hostn
            contentPath = url.partition('/')[2]

            # Create a temporary file on this socket and ask port 80 for the file requested by the client

        except Exception as e:
            # print(str(e))
            hostn = lastHost
            fileread = os.path.join(
                'cache', hostn + ' ' + url.replace('/', ' '))
            readsucc, _ = readfile(fileread, tcpCliSock, lastHost, hostn)
            if readsucc:
                continue
            try:
                c.connect((hostn, 80))

                contentPath = url
            except Exception:
                print(e)
                # tcpCliSock.send(b"HTTP/1.0 404 NOT FOUND\r\n")
                continue

        filename = (hostn + ' ' + contentPath).replace("/", ' ')
        filesave = os.path.join('cache', filename)

        getmsg = ("GET /" + contentPath + " HTTP/1.0\r\n\r\n").encode()
        print("Send:", getmsg)
        c.send(getmsg)
        # Read the response into buffer
        outputBuffer = b''
        while True:
            # c.settimeout(1)
            buf = c.recv(1024)
            # print(buf.decode())
            outputBuffer += buf
            if not buf:
                break
        # print(outputBuffer)
        # Create a new file in the cache for the requested file.
        # Also send the response in the buffer to client socket and the corresponding file in the cache
        try:
            with open(filesave, "wb") as tmpFile:
                tmpFile.write(outputBuffer)
        except Exception as e:
            print(e)
        tcpCliSock.send(outputBuffer)
        c.close()

    tcpCliSock.close()
# Fill in start.
tcpSerSock.close()
# Fill in end.
