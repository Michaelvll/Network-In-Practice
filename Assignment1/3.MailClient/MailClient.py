#!/usr/bin/env python3
import socket
import auth
import base64
import ssl


def sendRecv(clientSocket, msg, returnCode=None):
    if msg:
        if isinstance(msg, str):
            msg = msg.encode()
        clientSocket.send(msg)
        # print('C:', msg)
    if returnCode:
        recv = clientSocket.recv(1024).decode()
        if recv[:3] != returnCode:
            print('{} reply not received from server'.format(returnCode))
        return recv


msg = "\r\n I love computer networks!"
endmsg = "\r\n.\r\n"
# Choose a mail server (e.g. Google mail server) and call it mailserver
mailserver = auth.qq_server
user = auth.qq_user
password = auth.qq_password
mail_from = 'wz.wzh@qq.com'
mail_to = 'wz.wzh@sjtu.edu.cn'


header = "From: \"ZhanghaoWu\" <{}>\r\nTo: \"ZhanghaoWu\"<{}>\r\nSubject: Test\r\n".format(
    mail_from, mail_to)
MIME_header = '\r\n'.join(['MIME-Version: 1.0',
                           'Content-Type: multipart/mixed; boundary="BOUNDARY"',
                           '\r\n'])
text_header = '\r\n'.join(['\r\n',
                           '--BOUNDARY',
                           'Content-Type: text/plain; charset=utf-8',
                           #    'Content-Transfer-Encoding:base64',
                           '\r\n'])
img_header = '\r\n'.join(['\r\n',
                          '--BOUNDARY',
                          'Content-Type: image/png; name={}',
                          'Content-Transfer-Encoding: base64',
                          '\r\n'
                          ])


# Create socket called clientSocket and establish a TCP connection with mailserver
clientSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_STREAM)
clientSocket.connect(mailserver)


recv = sendRecv(clientSocket, None, '220')
print(recv)
# Send HELO command and print server response.
heloCommand = 'HELO sjtu.edu.cn\r\n'
recv1 = sendRecv(clientSocket, heloCommand, '250')
print(recv1)

# Send STARTTLS command and print server response.
tlsRecv = sendRecv(clientSocket, 'STARTTLS\r\n', '220')
clientSocket = ssl.wrap_socket(clientSocket)

# Send HELO command and print server response.
recv2 = sendRecv(clientSocket, heloCommand, '250')
print(recv2)


# Send AUTH LOGIN command and print server response.
authRecv = sendRecv(clientSocket, 'AUTH LOGIN\r\n', '334')
print(authRecv)
userRecv = sendRecv(clientSocket, base64.b64encode((
    user).encode()) + b'\r\n', '334')
print(userRecv)
pwdRecv = sendRecv(clientSocket, base64.b64encode((
    password).encode()) + b'\r\n', '235')
print(pwdRecv)


# Send MAIL FROM command and print server response.
mailRecv = sendRecv(
    clientSocket, 'MAIL FROM: <{}>\r\n'.format(mail_from), '250')
print(mailRecv)
# Send RCPT TO command and print server response.
rcptRecv = sendRecv(
    clientSocket, 'RCPT TO: <{}>\r\n'.format(mail_to), '250')
print(rcptRecv)

# Send DATA command and print server response.
dataRecv = sendRecv(clientSocket, 'DATA\r\n', '354')
print(dataRecv)
# Send MIME header
sendRecv(clientSocket, header)
sendRecv(clientSocket, MIME_header)

# Send image data.
img = 'test.png'
sendRecv(clientSocket, img_header.format(img))
with open(img, 'rb') as infile:
    img_data = base64.b64encode(infile.read())
sendRecv(clientSocket, img_data)


# Send message data.
sendRecv(clientSocket, text_header)
sendRecv(clientSocket, msg)


# Send MIME end data
MIME_end = '\r\n\r\n--BOUNDARY--\r\n\r\n'
sendRecv(clientSocket, MIME_end)

# Message ends with a single period.
sendRecv(clientSocket, endmsg, '250')

# Send QUIT command and get server response.
quitRecv = sendRecv(clientSocket, 'QUIT\r\n', '221')
print(quitRecv)

clientSocket.close()
