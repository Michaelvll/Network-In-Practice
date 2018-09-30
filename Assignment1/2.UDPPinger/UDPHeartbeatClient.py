# UDPHeartbeatClient.py
import random
import socket
import datetime
import time

clientSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
address = ('localhost', 12000)

rtts = []
loss_cnt = 0

seqID = 0

while True:
    time.sleep(1)
    rand = random.randint(0, 10)
    seqID += 1
    message = ' '.join(
        ['Heartbeat ', str(seqID), str(time.time())])
    if rand < 4:
        continue
    clientSocket.sendto(message.encode(), address)
    print("send Heartbeat:", message)


clientSocket.close()
