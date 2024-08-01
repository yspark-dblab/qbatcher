# QBatcher: Learning to Construct Cost-efficient Batches of Small Queries in Spark

This is a guide for running QBatcher on Apache Spark in a distributed Docker environment.

## 1. Prerequisites

This manual is structured on the assumption that the following environment has been established. If you do not have the following environment, configure it and follow the manual below.

- Multiple Linux-based servers. (e.g. Ubuntu, CentOS, ..)
- Each server must have [docker](https://docs.docker.com/get-docker/) installed, and the user must have access to this docker.
- Each server must be able to communicate with each other over the network and must be connected to the Internet.
- Among servers, the server to be used as the Master must be selected, and the server's IP must be identified.
- The ssh connection from the master to each worker must be possible.


## 2. Caution
##### (0) Testing environment.
Each machine of the cluster has Intel Xeon E5-2450 CPU with 16 cores and 192 GB of RAM.
On the software side, we installed Spark 3.0 and Hadoop 2.7.7 on Ubuntu 20.04.3 LTS.
Please keep the disk usage rate not exceeding 80% before the execution.
Otherwise, it is impossible to initialize Spark.

##### (1) Code execution location.
The codes to be executed are written in a code block and have the following format.
```
[MACHINE]$ command
```
Here, `MACHINE` has the following types. You can proceed by following the manual while checking whether the location where the code is executed is running in the correct location.

- master : Master node.
- master-docker : Inside docker container running on master node.
- worker : Worker node.
- worker-docker : Inside docker container running on worker node.

##### (2) Repeat the same task for all workers.
In a distributed programming environment, in most cases there are multiple workers, so you have to iterate over each worker machine with the same setup. Name each machine in the list of worker machines in the following order: `worker1`, `worker2`, `worker3`,... In addition, the part marked with `workerX` in the manual below means to repeatedly execute the instruction by substituting a worker number of `X = 1,2,3...` for each worker machine.

## 2. Docker Swarm
This step is to group each machine where Docker is installed into one cluster.

1. Connect via ssh to the master node.
2. Enter the following command.

```
[master]$ docker swarm init --advertise-addr MASTER_IP
```
When input, the message `Swarm initialized...` appears, and the command to run the `docker swarm join ...` command appears. Copy the entire command.

3. (by ssh connection to each worker node) Paste the copied command and execute it. This process is the process of tying each worker into one network.

```
(example)
[worker]$ docker swarm join \
    --token SWMTKN-1-49nj1cmql0jkz5s954yi3oex3nedyz0fb0xx14ie39trti4wxv-8vxv8rssmk743ojnwacrr2e7c \
    MASTER_IP:2377
```

4. (Back to the master node) Enter the following command.
```
[master]$ docker node ls
```

When you do this, all machines (including master) to be included in the cluster should be displayed.


5. Enter the following command.
```
[master]$ docker network create -d overlay --attachable spark-cluster-net
```


## 3. Create Docker Image and Run Container
This step is the process of downloading the configuration file, creating a Docker image, and running the container.

#### For Master, do the following:

1. Download this repository on the master machine.

2. Download two datasets (i.e., BRA and eBay datasets) on `qbatcher/datasets`.

- Download [BRA dataset directory](https://drive.google.com/drive/folders/1bSIu7spNZz-Z4QJcxINpqgFACjPju8FG?usp=sharing) on `qbatcher/datasets`

- Download [eBay dataset directory](https://drive.google.com/drive/folders/100oZ0kxfVsGSAAMR_D7PCBqM8rPYKZnQ?usp=sharing) on `qbatcher/datasets`

3. Download two query sets and their parameters on `qbatcher/querysets`

- Download [brazilian-ecommerce directory](https://drive.google.com/drive/folders/1LASZX5tokmJ21WyNN9qRcSolbOIF9iHf?usp=sharing) on `qbatcher/querysets`

- Download [eBay directory](https://drive.google.com/drive/folders/11eIYEIJGNBe5H4R0B-Lie-Lfzn0t4WgI?usp=sharing) on `qbatcher/querysets`

4. Download the master docker image [base-image-master.tar](https://drive.google.com/file/d/15zpJUCLjk--bhMqd_oYo2IES7hVVQS9-/view?usp=sharing) and load the docker image.
```
[master]$ docker load < base-image-master.tar
```

5. Run the docker container. (Here, users put the path of the repository into `PATH_TO_DIR`)
```
[master]$ docker run -itd --privileged -h master -v PATH_TO_DIR:/root/qbatcher --name master --net spark-cluster-net base-image-master
```

6. Enter the docker container.
```
[master]$ docker exec -it master /bin/bash
```

7. Enter the following commands and exit the docker container.
```
[master-docker]$ /root/init.sh  
[master-docker]$ exit
```

#### After the setup for master node, run the following manual for each worker machine.

1. Download the worker docker image [base-image-worker.tar](https://drive.google.com/file/d/1OuD5q8e1XWkB--UzNV1bl1-IfhXF8aRr/view?usp=sharing) on each worker machine.

2. Load the docker image.
```
[worker]$ docker load < base-image-worker.tar
```

3. Run the docker container. (Note `workerX` here. Replace X with the number of each worker node (1,2,3..).)
```
[worker]$ docker run -itd --privileged -h workerX --name workerX --link master:master --net spark-cluster-net base-image-worker
```

4. Enter the docker container. (Note `workerX`.)
```
[worker]$ docker exec -it workerX /bin/bash
```

5. Enter to `/root/dev/hadoop-2.7.7/etc/hadoop` and modify the corresponding files as follows.
###### yarn-site.xml

Add the configurations as follows.

**A** : (Total memory for each worker node) - 2048 (MB)

**B** : (Number of logical cores in each worker node) - 2

In the above case, it is a setting to use the maximum resources that each worker node can use. In the case of the Worker node, almost all resources are allocated to this task because almost no work other than performing distributed computation is performed. The reason for subtracting some values here is to reserve resources for system processes (all processes except YARN container).

```
    ...
    <property>
        <name>yarn.nodemanager.resource.memory-mb</name>
        <value>A</value>
    </property>
    ...
    <property>
        <name>yarn.nodemanager.resource.cpu-vcores</name>
        <value>B</value>
    </property>
    ...
    <property>
        <name>yarn.nodemanager.resource.resource.cpu-vcores</name>
        <value>B</value>
    </property>
    ...
```

###### core-site.xml

Change the configurations as follows.
```
    <property>
        <name>hadoop.tmp.dir</name>
        <value>/root/qbatcher/tmp</value>
    </property>
```

###### hdfs-site.xml

Change the configurations as follows.
```
    <property>
        <name>dfs.datanode.name.dir</name>
        <value>/root/qbatcher/datanode</value>
    </property>
```

6. Generate the password of the account.
```
[worker-docker]$ passwd
```

7. Enter the following commands and exit the docker container.
```
[worker-docker]$ /root/init.sh  
[worker-docker]$ exit
```

## 4. Configure SSH Connection
This step is for ssh communication between machines inside the Docker network.

1. Enter the master docker container.
```
[master]$ docker exec -it master /bin/bash
```

2. Repeat the following for all workers. In this process, a password is requested. Enter the account password set in 3.6. (note `workerX`)
```
[master-docker]$ ssh-copy-id -i /root/.ssh/id_rsa.pub root@workerX
```

## 5. Run Hadoop and Spark

This step initializes HDFS and runs Hadoop and Spark services.

1. Enter the master docker container.
```
[master]$ docker exec -it master /bin/bash
```

2. Enter `/root/dev/hadoop-2.7.7/etc/hadoop/` and modify each file as follows.
###### yarn-site.xml

**A**, **B** : (Total memory of the master node) - 4096 (MB)

**C**, **D** : (Total number of logical cores of the master node) - 4 

In the above case, it is a setting to use the appropriate resources that the master node can use. In the case of the master node, since it performs the execution order and resource management for the entire distributed application, an appropriate amount of resources is allocated in consideration of this part, and the above setting with a certain amount of consideration for the appropriate amount is applied.

```
    ...
    <property>
        <name>yarn.scheduler.maximum-allocation-mb</name>
        <value>A</value>
    </property>
    ...
    <property>
        <name>yarn.nodemanager.resource.memory-mb</name>
        <value>B</value>
    </property>
    ...
    <property>
        <name>yarn.nodemanager.resource.cpu-vcores</name>
        <value>C</value>
    </property>
    ...
    <property>
        <name>yarn.nodemanager.resource.resource.cpu-vcores</name>
        <value>C</value>
    </property>
    ...
    <property>
        <name>yarn.scheduler.maximum-allocation-vcores</name>
        <value>D</value>
    </property>
    ...
```

###### slaves
Delete all lines written in the existing file, and record as follows according to the number of workers.
Here, if you want the master node to not only manage the Spark program but also perform the actual work (the role of the worker), add `master` to the top line.

```
worker1
worker2
worker3
.....
```

###### hdfs-site.xml

Change the configurations as follows.
```
    <property>
        <name>dfs.namenode.name.dir</name>
        <value>/root/qbatcher/namenode</value>
    </property>
```

3. Run the following commands in the docker container.
```
[master-docker]$ chmod 777 -R /root/qbatcher/
[master-docker]$ /root/qbatcher/scripts/setup.sh
``` 

## 6. Run Experiments
Run the scripts in `qbatcher/scripts`.

All three scripts below generate result files (*.dat) in the directory on [PATH_TO_OUTPUT_DIR] (e.g., /root/qbatcher/results).

```
[master-docker]$ cd /root/qbatcher/scripts; ./run-test.sh [PATH_TO_OUTPUT_DIR]
```
