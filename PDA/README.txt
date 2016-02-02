
* Directory Structure
  - README.txt: This file.
  - src: directory that contains the java source code.
  - build.xml: the ant build file
	
* Build instructions:
  ** To build the program, Type: 
  	ant

* Run as the server: 
  ** To run the program as a syncCFT server, listening on port 7777, and the shared directory is "./SharedDir" 
	`java -cp "./dist/pda.jar;lib/*" SyncCFTServerApp -l 7777 -d ./SharedDir`

	If the environment is linux, use:
	`java -cp "./dist/pda.jar:lib/*" SyncCFTServerApp -l 7777 -d ./SharedDir`
	
  **** To add the loss model to the server, add noLoss2loss transition rate p and loss2loss transition rate q:
   java -cp "./dist/pda.jar;lib/*" SyncCFTServerApp -l 7777 -d ./SharedDir -p 0.1 -q 0.1
  
* Run as the client:  
  ** To run the program as a syncCFT client, and get directory information with host 86.50.139.63:7777
	java -cp "./dist/pda.jar;lib/*" SyncCFTClientApp -p 7777 -h 86.50.139.63 -l 6666 -d ./SharedDir -c SyncDir
	
	java -cp "./dist/pda.jar:lib/*" SyncCFTClientApp -p 7777 -h 86.50.140.253 -l 6666 -d /home/leo/sharedfolder -c SyncDir
	
  ** To run the program as a syncCFT client, and get a particular file with host 86.50.139.63:7777
  	java -cp "./dist/pda.jar;lib/*" SyncCFTClientApp -p 7777 -h 86.50.139.63 -l 6666 -d ./SharedDir -c GetFile -f someFile
	
	java -cp "./dist/pda.jar:lib/*" SyncCFTClientApp -p 7777 -h 86.50.140.255 -l 6666 -d /home/leo/sharedfolder -c GetFile -f BenHouse.jpg -s 8342517

  ** To run the program as a syncCFT client and pull the latest updates from host 86.50.139.63:7777
  	java -cp "./dist/pda.jar;lib/*" SyncCFTClientApp -p 7777 -h 86.50.139.63 -l 6666 -d ./SharedDir -c Update

* Run as both client and server and do regular automatic sync with a configured peer:
 
  java -cp "./dist/pda.jar;lib/*" SyncCFTApp -l 7777 -d ./SharedDir -c 86.50.129.25:7777
  
* Maintaining metadata:
  ** To add a file in shared directory to the metadata so that it can be shared with other peers:
	java -cp "./dist/pda.jar;lib/*" DirManageApp add ./SharedDir helsinki.jpg  
  
  ** To add modification information of a file in shared directory to the metadata so that it can be shared with other peers:
  	java -cp "./dist/pda.jar;lib/*" DirManageApp modify ./SharedDir helsinki.jpg
  
  ** To delete a file from the shared directory, and mark this to the metadata so that it can be shared with other peers:
	java -cp "./dist/pda.jar;lib/*" DirManageApp delete ./SharedDir helsinki.jpg
	
* External Dependencies
	- Ant: This project assumes ANT has been installed in the system.
