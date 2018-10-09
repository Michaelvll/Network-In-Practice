from socket import *
import os
import sys
import threading
# Create a server socket, bind it to a port and start listening
use_localhost = False


def readfile(fileread, tcpCliSock, hostn):
    if os.path.isfile(fileread):
        print("fileread:", fileread)
        # Check wether the file exist in the cache
        with open(fileread, "rb") as f:
            outputdata = f.read()
            # ProxyServer finds a cache hit and generates a response message
            # tcpCliSock.send(b"HTTP/1.0 200 OK\r\n")

            # tcpCliSock.send(b"Content-Type:text/html; charset=UTF-8\r\n")
            tcpCliSock.send(outputdata)
            print('Read from cache')
        return True
    return False


def proxyServe(tcpCliSock, addr):
    c = socket(AF_INET, SOCK_STREAM)

    try:

        # Strat receiving data from the client
        message = tcpCliSock.recv(1024).decode()
        if message == '':
            # lastHost = ''
            return
        print("Get message: ", [message])
        # Extract the filename from the given message
        if use_localhost:
            url = message.split()[1].partition("/")[2]
        else:
            url = message.split()[1].partition("//")[2]
        if 'google' in message:
            return
        print("URL:", url)
        fileread = os.path.join('cache', url.replace(
            "www.", "", 1).replace('/', ' '))
        hostn = url.replace("www.", "", 1).split('/')[0]
        readsucc = readfile(fileread, tcpCliSock, hostn)
        if not readsucc:
            hostn = url.replace("www.", "", 1).split('/')[0]

            # Create a socket on the proxyserver

            try:
                # Connect to the socket to port 80
                c.connect((hostn, 80))
                contentPath = url.partition('/')[2]

                # Create a temporary file on this socket and ask port 80 for the file requested by the client

            except Exception as e:
                # print(str(e))
                fileread = os.path.join(
                    'cache', hostn + ' ' + url.replace('/', ' '))
                readsucc = readfile(fileread, tcpCliSock,  hostn)
                if readsucc:
                    c.close()
                    return
                try:
                    c.connect((hostn, 80))

                    contentPath = url
                except Exception:
                    print(e)
                    # tcpCliSock.send(b"HTTP/1.0 404 NOT FOUND\r\n")
                    c.close()
                    return

            filename = (hostn + ' ' + contentPath).replace("/", ' ')
            filesave = os.path.join('cache', filename)

            getmsg = ("GET /" + contentPath + " HTTP/1.0\r\n\r\n").encode()
            print("Send:", getmsg)

            c.send(getmsg)
            # Read the response into buffer
            outputBuffer = b''
            while True:
                # c.settimeout(1)
                buf = c.recv(4096)
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
    except timeout:
        print("timeout exit")
        c.close()
    tcpCliSock.close()


if __name__ == "__main__":
    serverSocket = socket(AF_INET, SOCK_STREAM)
    serverSocket.bind(('', 6060))
    serverSocket.listen(5)
    if not os.path.exists('cache'):
        os.makedirs('cache')
    cnt = 0
    while True:
        # Establish the connection
        # print('Ready to serve...')
        connectionSocket, addr = serverSocket.accept()
        connectionSocket.settimeout(5)
        cnt += 1
        print("thread:", cnt)
        threading.Thread(target=proxyServe,
                         args=(connectionSocket, addr)).start()
        # proxyServe(connectionSocket, addr)

    serverSocket.close()
