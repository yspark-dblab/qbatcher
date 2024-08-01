passwd=Asdf123!
echo "${passwd}" > /root/qbatcher/scripts/passwd
current_date=$(date +'%Y-%m-%d %H:%M:%S')
date -s "$(echo ${current_date})"

for i in {0..10}; do
  while read -u 3 host; do
    echo ${host}
    command="rm -rf /tmp/*; rm -rf /root/qbatcher/datanode; mkdir -p /root/qbatcher/datanode; rm -rf /root/qbatcher/tmp; mkdir -p /root/qbatcher/tmp; rm -rf /root/dev/hadoop-2.7.7/logs/*; yes | /root/dev/hadoop-2.7.7/bin/hdfs namenode -format"
    echo ${command}
    sshpass -f /root/qbatcher/scripts/passwd ssh -tt root@${host} "$command"
    command='date -s "$(echo ${current_date})"'
    sshpass -f /root/qbatcher/scripts/passwd ssh -tt root@${host} "$command"
  done 3< /root/dev/hadoop-2.7.7/etc/hadoop/slaves

  rm -rf /root/qbatcher/scripts/passwd

  rm -rf /tmp/*; rm -rf /root/qbatcher/namenode; mkdir -p /root/qbatcher/namenode; rm -rf /root/qbatcher/tmp; mkdir -p /root/qbatcher/tmp; rm -rf /root/dev/hadoop-2.7.7/logs/*

  rm -rf /root/qbatcher/scripts/core.*
  rm -rf /root/qbatcher/scripts/hs_*log
  rm -rf /root/qbatcher/scripts/target
  pkill -9 -f 'java'
  pkill -9 -f 'sleep'

  /root/qbatcher/scripts/setup.sh

  sleep 10

  num_nodes=$(/root/dev/hadoop-2.7.7/bin/hdfs dfsadmin -report | grep "Normal" | wc -l)
  echo "num_nodes: ${num_nodes}"
  /root/dev/hadoop-2.7.7/bin/hdfs dfsadmin -report
  target=$(cat /root/dev/hadoop-2.7.7/etc/hadoop/slaves | wc -l)
  if [[ ${num_nodes} = ${target} ]]; then
    echo "This is a valid cluster."
    break
  fi
done
if [[ ${i} == "10" ]]; then
  echo "This is a invalid cluster."
fi
