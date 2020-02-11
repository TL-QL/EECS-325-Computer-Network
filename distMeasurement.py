import socket
import struct
import select
import time
import csv
from socket import SOCK_RAW, AF_PACKET
import matplotlib.pyplot as plt

# Plot data with matplotlib
def plot(x, y, ax, title, x_label, y_label):
    ax.set_title(title)
    ax.set_ylabel(x_label)
    ax.set_ylabel(y_label)
    ax.plot(x, y)

def main():
    IP = '3.90.6.45'
    PORT = 33434
    msg = "measurement for class project. questions to student qxl216@case.edu or professor mxr136@case.edu"
    payload = bytes(msg+'a'*(1472 - len(msg)), 'ascii')
    hostnames = []
    iPs = []
    measurements = []

    # Read targets.txt
    f = open('targets.txt', 'r')
    this_line = f.readline()
    while this_line != '':
        temp = this_line.strip('\n')
        hostnames.append(temp)
        iPs.append(socket.gethostbyname(temp))
        this_line = f.readline()
    f.close()

    # Set up sender socket to send ICMP request
    # Set up receiver socket to receive ICMP response
    send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    send_sock.setsockopt(socket.SOL_IP, socket.IP_TTL, 128)
    recv_sock = socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_ICMP)
    recv_sock.settimeout(3)
    recv_sock.bind(("",0))

    # Measure RTT and hop count for each site chosen
    for i in range(len(hostnames)):
        measurement = {}
        hostname = hostnames[i]
        ip = iPs[i]

        print("%i. " %(i+1))
        print("This is " + hostname+"! IP = "+ip)

        myTry = 0
        hasFinished = False
        start_send_time = time.time()
        send_sock.sendto(payload, (ip, PORT))

        # Measure RTT and hop count for one site
        # Try 3 times if cannot get response immediately
        while myTry < 3 and not hasFinished:
            
            numMatch = 0

            # Packet received with ICMP message
            icmp_packet = recv_sock.recv(1500)

            # Calculate RTT
            end_receive_time = time.time()
            measured_rtt = (end_receive_time - start_send_time) * 1000
            measurement['RTT'] = measured_rtt

            # Calculate hop count
            rcv_TTL = icmp_packet[36]
            measured_hop = 128 - rcv_TTL
            measurement['HOP'] = measured_hop

            # Calculate original bytes
            original_byte = len(icmp_packet)-28
            measurement['OriginalBytes'] = original_byte

            # Check whether the ICMP type and code are correct
            # Try to match ICMP responses with the probe
            icmp_type = icmp_packet[20]
            icmp_code = icmp_packet[21]
            source_ip = socket.inet_ntoa(icmp_packet[12:16])
            port_from_packet = struct.unpack("!H", icmp_packet[50:52])[0]
            if(icmp_type == 3 and icmp_code == 3):
                if(source_ip == ip):
                    numMatch += 1
                if(port_from_packet == PORT):
                    numMatch += 1
            if(numMatch > 0):
                measurements.append(measurement)
                print("Measured RTT = %f ms" % measured_rtt)
                print("Measured Hop = %i" % measured_hop)
                print("Tech match = %i" % numMatch)
                print("The number of bytes of the original datagram = %i" % original_byte)
                print("\n")
                hasFinished = True
            else:
                print("Unexpected ICMP packet!")
                myTry += 1  
            
        if not hasFinished:
            print("Oops! Time out!")
        
    send_sock.close()
    recv_sock.close()

    # write data to csv file
    fields = ['HOP', 'RTT', 'OriginalBytes']
    with open("output.csv", 'w') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames = fields)
        writer.writeheader()
        writer.writerows(measurements)

    # Plot data with matplotlib
    x=[]
    y=[]
    with open('output.csv', 'r') as csvfile:
        plots= csv.reader(csvfile, delimiter=',')
        for row in plots:
            x.append(row[0])
            y.append(row[1])

    fig, ax = plt.subplots()
    plot(x, y, ax, "RTT vs Hop", "Hop", "RTT(ms)")
    fig.savefig('RTT_vs_Hop.png')

if __name__ == "__main__":
   main()