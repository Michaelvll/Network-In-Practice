# UDPPingerServer.py
# We will need the following module to generate randomized lost packets
import random
import socket
import time
# Create a UDP socket
# Notice the use of SOCK_DGRAM for UDP packets
serverSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
# Assign IP address and port number to socket
serverSocket.bind(('', 12000))
data = serverSocket.recv(1024).decode()
print("Start Heartbeat:", data)
_, lastSeqID, timeStr = data.split()
lastSeqID, lastTime = int(lastSeqID), float(timeStr)
while True:
    try:
        serverSocket.settimeout(5)

        data = serverSocket.recv(1024).decode()
        _, seqID, time = data.split()
        seqID, time = int(seqID), float(time)
        print("Get Heartbeat: ", data)
        print("Duration:", time - lastTime, "\t Loss:",
              seqID - lastSeqID - 1, end="\n\n")
        lastSeqID, lastTime = seqID, time
    except socket.timeout:
        print("Client lost for 5 seconds, stop!")
        break

serverSocket.close()
