m=128
dataset=$1
training_file_path=$2
test_file_path=$3
log_file_path=$4
cluster_file_path=$5
regression_model_type=$6
num_iter=$7
clustering_model_type=$8
num_clusters1=$9
num_clusters2=${10}
feature_cols_for_query_cost=${11}
feature_cols_for_clustering1=${12}
feature_cols_for_clustering2=${13}
label_col=${14}
num_queries=${15}

training_file_path_for_batch_scheduler=${16}
regression_model_type_for_batch_scheduler=${17}
feature_cols_for_batch_scheduler=${18}
num_iter_for_batch_scheduler=${19}
num_batch_col1=${20}
num_batch_col2=${21}
label_col_for_batch_scheduler=${22}

if [ ${num_clusters1} != -1 ]; then
	training_file_path_for_batch_scheduler=" "
	regression_model_type_for_batch_scheduler=" "
	feature_cols_for_batch_scheduler=" "
	num_iter_for_batch_scheduler=20
	num_batch_col1=" "
	num_batch_col2=" "
	label_col_for_batch_scheduler=" "
else
	dir3=$(dirname ${training_file_path_for_batch_scheduler})
	hdfs dfs -rm ${training_file_path_for_batch_scheduler}
	hdfs dfs -put ${training_file_path_for_batch_scheduler} ${training_file_path_for_batch_scheduler}
fi

hdfs dfs -rm -r -f /root/qbatcher/results/predictions-${dataset}.csv
hdfs dfs -mkdir /root/qbatcher/results
hdfs dfs -put /root/qbatcher/results/predictions-${dataset}.csv /root/qbatcher/results/predictions-${dataset}.csv

# echo all arguments
echo "arguments of run-cost-model-yarn.sh:"
echo "dataset: ${dataset}"
echo "training_file_path: ${training_file_path}"
echo "test_file_path: ${test_file_path}"
echo "cluster_file_path: ${cluster_file_path}"
echo "regression_model_type: ${regression_model_type}"
echo "num_iter: ${num_iter}"
echo "clustering_model_type: ${clustering_model_type}"
echo "num_clusters1: ${num_clusters1}"
echo "num_clusters2: ${num_clusters2}"
echo "feature_cols_for_query_cost: ${feature_cols_for_query_cost}"
echo "feature_cols_for_clustering1: ${feature_cols_for_clustering1}"
echo "feature_cols_for_clustering2: ${feature_cols_for_clustering2}"
echo "label_col: ${label_col}"
echo "num_queries: ${num_queries}"
echo "training_file_path_for_batch_scheduler: ${training_file_path_for_batch_scheduler}"
echo "regression_model_type_for_batch_scheduler: ${regression_model_type_for_batch_scheduler}"
echo "feature_cols_for_batch_scheduler: ${feature_cols_for_batch_scheduler}"
echo "num_iter_for_batch_scheduler: ${num_iter_for_batch_scheduler}"
echo "num_batch_col1: ${num_batch_col1}"
echo "num_batch_col2: ${num_batch_col2}"
echo "label_col_for_batch_scheduler: ${label_col_for_batch_scheduler}"

if [ ${dataset} = bra ]; then
  dir=brazilian-ecommerce
else
  dir=ebay
fi

dir1=$(dirname ${training_file_path})
dir2=$(dirname ${test_file_path})
hdfs dfs -mkdir -p ${dir1}
hdfs dfs -mkdir -p ${dir2}
hdfs dfs -mkdir -p ${dir3}
hdfs dfs -rm ${training_file_path}
hdfs dfs -put ${training_file_path} ${training_file_path}
hdfs dfs -rm ${test_file_path}
hdfs dfs -put ${test_file_path} ${test_file_path}

model_dir=/root/qbatcher/results/models
mkdir -p ${model_dir}
echo "regression ${dataset}"
/root/qbatcher/scripts/set.sh ori-jars
/root/dev/spark-3.2.1-bin-hadoop2.7/bin/spark-shell \
	--name regression-d-${dataset} \
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
	--conf "spark.driver.extraJavaOptions=-Dfile.encoding=UTF-8" \
	-i <(echo 'val trainingFilePath = "'${training_file_path}'"') \
	-i <(echo 'val testFilePath = "'${test_file_path}'"') \
	-i <(echo 'val numIter = "'${num_iter}'".toInt') \
	-i <(echo 'val regressionModelType = "'${regression_model_type}'"') \
	-i <(echo 'val featureColumns = "'${feature_cols_for_query_cost}'".split(",")') \
	-i <(echo 'val outputFilePath = "'${cluster_file}'"') \
	-i <(echo 'val numClusters1 = "'${num_clusters1}'".toInt') \
	-i <(echo 'val numClusters2 = "'${num_clusters2}'".toInt') \
	-i <(echo 'val featureColsForClustering1 = "'${feature_cols_for_clustering1}'".split(",")') \
	-i <(echo 'val featureColsForClustering2 = "'${feature_cols_for_clustering2}'".split(",")') \
	-i <(echo 'val dataset = "'${dataset}'"') \
	-i <(echo 'val labelCol = "'${label_col}'"') \
	-i <(echo 'val numQueries = "'${num_queries}'".toInt') \
	-i <(echo 'val clusteringAlgorithm = "'${clustering_model_type}'"') \
	-i <(echo 'val clusterFilePath = "'${cluster_file_path}'"') \
	-i <(echo 'val gTrainingFilePathForBatchScheduler = "'${training_file_path_for_batch_scheduler}'"') \
	-i <(echo 'val gRegressionModelTypeForBatchScheduler = "'${regression_model_type_for_batch_scheduler}'"') \
	-i <(echo 'val gFeatureColsForBatchScheduler = "'${feature_cols_for_batch_scheduler}'".split(",")') \
	-i <(echo 'val gNumIterForBatchScheduler = "'${num_iter_for_batch_scheduler}'".toInt') \
	-i <(echo 'val gNumBatchCol = ("'${num_batch_col1}'", "'${num_batch_col2}'")') \
	-i <(echo 'val gLabelColForBatchScheduler = "'${label_col_for_batch_scheduler}'"') \
  -i /root/qbatcher/src/KMeans.scala \
  -i /root/qbatcher/src/SimpleMLP.scala \
	-i /root/qbatcher/src/WorkloadCostModel.scala \
	-i /root/qbatcher/src/QueryCostModel.scala
