Port used 50582 - 50593
Server used eecslab-10.case.edu - eecslab-15.case.edu

From folder 0 to folder 5, they stores all files of 6 peers and they are also saved on servers.

For each query, it will wait for at most 60s for queryHit. Or it will return null. In some  cases, it will return null because query queue is too long to get response in time.

For Heat beat, it will sent every 300s.

For each client socket, it will close if no operation in 180s.