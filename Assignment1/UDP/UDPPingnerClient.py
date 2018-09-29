# UDPPingerClient.py
import random
import socket
import datetime

clientSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
address = ('localhost', 12000)

rtts = []
loss_cnt = 0

for i in range(1, 11):
    try:
        sendTime = datetime.datetime.now()
        message = ' '.join(
            ['Ping ', str(i), str(datetime.datetime.time(sendTime))])

        clientSocket.sendto(message.encode(), address)
        clientSocket.settimeout(1)
        recvData = clientSocket.recv(1024)

        getTime = datetime.datetime.now()
        rtt = (getTime - sendTime).total_seconds()

        rtts.append(rtt)

        print(recvData)
        print("RTT:", rtt, end='\n\n')
    except socket.timeout:
        loss_cnt += 1
        print("Request timed out\n")

print("Min RTT:", min(rtts), "\tMax RTT:",
      max(rtts), "\tAvg RTT:", sum(rtts) / 10)
print("Packet loss rate: {}%".format(loss_cnt * 10))
