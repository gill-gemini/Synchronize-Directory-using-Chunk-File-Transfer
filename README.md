# Synchronize-Directory-using-Chunk-File-Transfer

The objective of thisspecification is to propose a solution to maintain the synchronization of a
directory shared between two hosts.
We limit scope to the extent that there are only files, no sub-directories structures are allowed
under in this “shared” directory.
** Overview**

SyncCFT works according to a pull model where each host can:
● Pull the file information such as name, timestamp, etc. from its peers. This kind of functions
are called “Directory Info Synchronization Functions” (see section 4 for details). It helps
local host to find out which files it has are outdated and which files are created/deleted on a
remote host.
● Initiate file transfer requests to pull the latest copy of a shared file from remote host.

** Terminology**

*Directory Synchronization Functions:*
The commands and protocol messages used to get the latest directory info, i.e. list of files
and corresponding timestamps from the remote peer.
Data Transfer Functions:
The commands and protocol messages used to transfer files between two hosts.

File Sender:

The host that acts as the data provider and sends the file data over the network to another
node.

File Receiver:

The host on the recipient end that initiates the file data transfer session and ask the file
sender to send file to it.

chunk:

A chunk refers to a unit of file data being transferred between two hosts.

chunk size:

The size of a chunk of a file. In this spec, we set it to a hard-coded 1000 bytes.
chunk number:
Each chunk has a unique number that is of significance within a given data file being
transferred.

chunk group:

A group of chunks that is retrieved together signaled by the RETRIEVE transaction.
Retrieval session:
The process which started from the first RETRIEVE request (sent from file receiver to the
file sender to solicit a chunk group) and followed by the data transfer of a chunk group from the file
sender to the file receiver and ended by a THANKS request (sent from
