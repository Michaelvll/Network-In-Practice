# Mininet Assignment 3: ARP, ICMP and RIP

## Setup Environment
1. Install required packages
    ```bash
    sudo apt-get update
    sudo apt-get install -y python-dev python-setuptools flex bison ant openjdk-7-jdk git tmux
    ```
2. Install ltprotocol
    ```bash
    cd ~
    git clone git://github.com/dound/ltprotocol.git
    cd ltprotocol
    sudo python setup.py install
    ```
3. Checkout the appropriate version of POX
    ```bash
    cd ~
    git clone https://github.com/noxrepo/pox
    cd ~/pox
    git checkout f95dd1
    ```
4. Get the starter codes, replace the src folder with the one in the submit files and  
    ```
    cd ~/progAssign2
    ```
5. Symlink POX and configure the POX modules
    ```bash
    cd ~/progAssign2
    ln -s ../pox
    ./config.sh
    ```

6. Compile the java code and pack it with jar by running
    ```bash
    ant
    ```

## Test the codes
### Step 2: Virtual ICMP
I implemented the function of ICMP, and you can test it using the follow steps:
1. Start mininet:
   ```bash
    cd ~/progAssign3/
    sudo python ./run_mininet.py topos/single_rt.topo
   ```
2. Open another terminal and start the controller:
   ```bash
   cd ~/progAssign2/
   ./run_pox.sh
   ```

3. Start the java code:
    ```bash
     java -jar VirtualNetwork.jar -v r1
    ```
#### Time Exceeded
    
```bash
mininet> h1 ping -c 2 10.0.2.102
PING 10.0.2.102 (10.0.2.102) 56(84) bytes of data.
From 10.0.1.1 icmp_seq=1 Destination Host Unreachable
From 10.0.1.1 icmp_seq=2 Destination Host Unreachable
--- 10.0.2.102 ping statistics ---
```
#### Destination net unreachable
```bash
mininet> h1 ping -c 2 10.0.2.102
PING 10.0.2.102 (10.0.2.102) 56(84) bytes of data.
From 10.0.1.1 icmp_seq=1 Destination Net Unreachable
From 10.0.1.1 icmp_seq=2 Destination Net Unreachable
--- 10.0.2.102 ping statistics ---
2 packets transmitted, 0 received, +2 errors, 100% packet loss, time 1001ms
```
#### Destination host unreachable
```bash
mininet> h1 ping -c 2 10.0.2.102
PING 10.0.2.102 (10.0.2.102) 56(84) bytes of data.
From 10.0.1.1 icmp_seq=1 Destination Host Unreachable
From 10.0.1.1 icmp_seq=2 Destination Host Unreachable
--- 10.0.2.102 ping statistics ---
2 packets transmitted, 0 received, +2 errors, 100% packet loss, time 1001ms
```
#### Destination port unreachable
```bash
mininet> h1 wget 10.0.1.1
--2018-12-9 01:52:09--  http://10.0.1.1/
Connecting to 10.0.1.1:80... failed: Connection refused.
```

### Step 3: Implement ARP
I completed the function of ARP and it can be tested by the following steps:
1. Start mininet:
   ```bash
    cd ~/progAssign2/
    sudo python ./run_mininet.py topos/single_rt.topo
   ```
2. Open another terminal and start the controller:
   ```bash
   cd ~/progAssign2/
   ./run_pox.sh
   ```

3. Start the java code:
    ```bash
     java -jar VirtualNetwork.jar -v r1 -r rtable.r1
    ```

4. Go back to the terminal where Mininet is running:
   ```bash
   mininet> h1 ping -c 2 10.0.2.102
   PING 10.0.2.102 (10.0.2.102) 56(84) bytes of data.
    64 bytes from 10.0.2.102: icmp_seq=1 ttl=63 time=82.4 ms
    64 bytes from 10.0.2.102: icmp_seq=2 ttl=63 time=118 ms

    --- 10.0.2.102 ping statistics ---
    2 packets transmitted, 2 received, 0% packet loss, time 1002ms
    rtt min/avg/max/mdev = 82.407/100.368/118.330/17.964 ms
   ```
   ```bash
   mininet> h1 ping -c 2 10.0.2.103
   PING 10.0.2.103 (10.0.2.103) 56(84) bytes of data.
    From 10.0.1.1 icmp_seq=1 Destination Host Unreachable
    From 10.0.1.1 icmp_seq=2 Destination Host Unreachable

    --- 10.0.2.103 ping statistics ---
    2 packets transmitted, 0 received, +2 errors, 100% packet loss, time 1005ms
    ```
### Step 4: Implement RIP
I completed the function of RIP and it can be tested by the following steps:
1. Start mininet:
   ```bash
    cd ~/progAssign2/
    sudo python ./run_mininet.py topos/triangle_with_sw.topo
   ```
2. Open another terminal and start the controller:
   ```bash
   cd ~/progAssign2/
   ./run_pox.sh
   ```

3. Start the java code on each of the routers and switches

4. Go back to the terminal where Mininet is running:
    ```bash
    mininet> h1 ping -c 2 10.0.2.102
    PING 10.0.2.102 (10.0.2.102) 56(84) bytes of data.
    64 bytes from 10.0.2.102: icmp_seq=1 ttl=62 time=742 ms
    64 bytes from 10.0.2.102: icmp_seq=2 ttl=62 time=173 ms
    64 bytes from 10.0.2.102: icmp_seq=3 ttl=62 time=137 ms

    --- 10.0.2.102 ping statistics ---
    3 packets transmitted, 3 received, 0% packet loss, time 2000ms
    rtt min/avg/max/mdev = 137.563/351.244/742.899/277.325 ms
    ```
Though there are some bugs of pox which will show a lot of errors, the code can also handle the routers and switches in topos, including pair_rt.topo, triangle_rt.topo, single_each.topo and triangle_with_sw.topo.
