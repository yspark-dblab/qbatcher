m=128
dataset=$1
num_rows=$2
num_partitions=$3
query_ids_string=$5
fold_id=$6
if [ ${dataset} = bra ]; then
  num_templates=33
  dir=brazilian-ecommerce
else
  num_templates=27
  dir=ebay
fi
num_queries=$(python -c "print(int(float($4) / ${num_templates}))")
input_path=../scripts/input/${dataset}-${num_rows}.txt
echo "qaad ${num_rows} ${num_queries}"
./set.sh qaad-default
if [ ${dataset} = bra ]; then
	$SPARK_HOME/bin/spark-shell \
    --name qaad-d-${dataset}-r-${num_rows}-q-${num_queries}-p-${num_partitions} \
		--master yarn \
		--driver-memory ${m}g \
		--driver-cores 14 \
		--executor-cores 14 \
		--num-executors 4 \
		--executor-memory ${m}g \
		--conf spark.driver.maxResultSize=20g \
		--conf spark.scheduler.listenerbus.eventqueue.capacity=100000 \
		--conf spark.memory.fraction=0.8 \
		--conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
		--conf spark.kryoserializer.buffer.max=1g \
		--conf spark.rpc.message.maxSize=2000 \
		--conf spark.yarn.maxAppAttempts=1 \
		-i <(echo 'val inputPath = "'${input_path}'"') \
		-i <(echo 'val globalNumPartitions = "'${num_partitions}'".toInt') \
		-i <(echo 'val numQueries = "'${num_queries}'".toInt') \
		-i <(echo 'val numRows = "'${num_rows}'".toInt') \
		-i <(echo 'var globalStartTime = 0.0f') \
		-i <(echo 'val queryIds = "'${query_ids_string}'".split(",").map(_.toInt)') \
		-i <(echo 'val foldId = "'${fold_id}'".toInt') \
    -i /root/qbatcher/querysets/${dir}/UdfManager.scala \
		-i /root/qbatcher/src/Partitioners.scala \
		-i /root/qbatcher/src/Operation.scala \
		-i /root/qbatcher/src/MicroRDD.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp1.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp2.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp3.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp4.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp5.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp6.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp7.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp8.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp9.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp10.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp11.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp12.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp13.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp14.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp15.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp16.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp17.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp18.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp19.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp20.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp21.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp22.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp23.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp24.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp25.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp26.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp27.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp28.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp29.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp30.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp31.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp32.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp33.scala \
		-i /root/qbatcher/src/QaaDApp-${dataset}.scala > /dev/null 2>&1
else
	$SPARK_HOME/bin/spark-shell \
    --name qaad-d-${dataset}-r-${num_rows}-q-${num_queries}-p-${num_partitions} \
		--master yarn \
		--driver-memory ${m}g \
		--driver-cores 14 \
		--executor-cores 14 \
		--num-executors 4 \
		--executor-memory ${m}g \
		--conf spark.driver.maxResultSize=20g \
		--conf spark.scheduler.listenerbus.eventqueue.capacity=100000 \
		--conf spark.memory.fraction=0.8 \
		--conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
		--conf spark.kryoserializer.buffer.max=1g \
		--conf spark.rpc.message.maxSize=2000 \
		--conf spark.yarn.maxAppAttempts=1 \
		-i <(echo 'val inputPath = "'${input_path}'"') \
		-i <(echo 'val globalNumPartitions = "'${num_partitions}'".toInt') \
		-i <(echo 'val numQueries = "'${num_queries}'".toInt') \
		-i <(echo 'val numRows = "'${num_rows}'".toInt') \
		-i <(echo 'var globalStartTime = 0.0f') \
		-i <(echo 'val queryIds = "'${query_ids_string}'".split(",").map(_.toInt)') \
		-i <(echo 'val foldId = "'${fold_id}'".toInt') \
    -i /root/qbatcher/querysets/${dir}/UdfManager.scala \
    -i /root/qbatcher/src/Partitioners.scala \
		-i /root/qbatcher/src/Operation.scala \
		-i /root/qbatcher/src/MicroRDD.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp1.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp2.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp3.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp4.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp5.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp6.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp9.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp10.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp11.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp12.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp13.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp14.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp15.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp16.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp17.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp18.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp19.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp20.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp24.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp26.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp27.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp28.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp29.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp30.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp31.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp32.scala \
		-i /root/qbatcher/querysets/${dir}/DashboardApp33.scala \
		-i /root/qbatcher/src/QaaDApp-${dataset}.scala > /dev/null 2>&1
fi
