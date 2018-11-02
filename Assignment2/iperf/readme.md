## Usage

For convenience, I have already generated a jar file in the bin folder. You can run the Iperfer.jar by the commands below. Also, you can compile and execute the codes by the command show in the next session.
1. For client
    ```bash
    java -jar bin/Iperfer -c -h hostname -p port -t time
    ```
    e.g. ```java -jar bin/Iperfer.jar -c -h 10.0.0.1 -p 10000 -t 3```, which will try to connect to the *10.0.0.1:10000*, send chunks with 1000 bytes continuously for 3 seconds and print a summary of the speed.
2. For server
    ```bash
    java -jar bin/Iperfer -s -p port
    ```
    e.g. ```java -jar bin/Iperfer -s -p 10000```, which will listen to the port *10000*, receive byte chunks from the client and print a summary of the speed.

## Compile and Execute (Optional)

### Compile

The following is the step to compile the codes:
```bash
mkdir -p bin
javac src/*.java -d bin -cp "lib/*"
```

### Execute

Now you can execute the files in bin by the commands below.
1. For client
   ```bash
    java -cp lib/*:bin Iperfer -c -h hostname -p port -t time
    ```
    e.g. ```java -cp lib/*:bin Iperfer -c -h 10.0.0.1 -p 10000 -t 3```, which will try to connect to the *10.0.0.1:10000*, send chunks with 1000 bytes continuously for 3 seconds and print a summary of the speed.
2. For server
    ```bash
    java -cp lib/*:bin Iperfer -s -p port
    ```
    e.g. ```java -cp lib/*:bin Iperfer -s -p 10000```, which will listen to the port *10000*, receive byte chunks from the client and print a summary of the speed.



