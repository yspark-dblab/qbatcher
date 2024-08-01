#!/bin/bash

output_dir=$1
duration=30
num_iter=10
num_rows=0
num_partitions=448
num_queries_list1=(33 66 132 264 528 1056)
num_queries_list2=(27 54 108 216 432 864)

cost_model_type=decisionTree
card_est_model_type=decisionTree
clustering_model_type=KMeans
dataset_dir=/root/qbatcher/datasets
feature_columns_for_card_est="col1,col35,col36,col37,col12,col17,col18,col19,col20,col21,col22,col23,col24,col25,col26,col27,col28,col29,col30,col31,col32,col33"
label_col_for_card_est="col2"
feature_columns_for_query_cost="col43,col6,col7,col8,col9,col3,col4,col5,col0,col11,col1,col12,col2,col13,col14,col15,col10,col35,col36,col37,col38,col39,col40,col41,col42,col44,col45,col46,col47,col48"
label_col_for_for_query_cost="col34"
feature_columns_for_clustering1="prediction"
regression_model_type_for_batch_scheduler=simpleMlp
feature_cols_for_batch_scheduler="col0,col1,col2,col3,col4,col5,col6,col7,col8,col9,col10,col11,col12,col13,col14,col15,col16,col17,col18,col19"
num_iter_for_batch_scheduler=20
num_batch_col1="col18"
num_batch_col2="col19"
label_col_for_batch_scheduler="col20"

num_clusters_list=("-1,-1")

mkdir -p ${output_dir}
for dataset in bra ebay; do
  if [ ${dataset} = bra ]; then
    num_queries_list=("${num_queries_list1[@]}")
    feature_columns_for_clustering2="s0,s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12,s13,s14,s15,s16,s17,s18,s19,s20,s21,s22,s23,s24,s25,s26,s27,s28,s29,s30,s31,s32"
  else
    num_queries_list=("${num_queries_list2[@]}")
    feature_columns_for_clustering2="s0,s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12,s13,s14,s15,s16,s17,s18,s19,s20,s21,s22,s23,s24,s25,s26"
  fi
  training_file_path=/root/qbatcher/datasets/${dataset}-training-dataset.csv
  test_file_path=/root/qbatcher/datasets/${dataset}-test-dataset.csv
  max_num_queries=${num_queries_list[${#num_queries_list[@]}-1]}
  training_file_path_for_batch_scheduler=/root/qbatcher/datasets/${dataset}-training-dataset-for-batch-scheduler.csv
  bash /root/qbatcher/scripts/clear-tmp-files.sh
  bash /root/qbatcher/scripts/run-gen-partitions.sh ${dataset} ${num_rows} ${num_partitions} > /dev/null 2>&1
  dirname=$(dirname ${training_file_path})
  hdfs dfs -rm -r -f ${training_file_path}
  hdfs dfs -mkdir -p ${dirname}
  hdfs dfs -put ${training_file_path} ${training_file_path}
  for num_queries in ${num_queries_list[@]}; do
    for num_cluster_pair in "${num_clusters_list[@]}"; do
      IFS=',' read -ra split_num_cluster_pair <<< "$num_cluster_pair"
      num_clusters1="${split_num_cluster_pair[0]}"
      num_clusters2="${split_num_cluster_pair[1]}"
      # cluster generation (query cost estimation & clustering)
      cluster_file_path=${output_dir}/cluster-d-${dataset}-m-${cost_model_type}-cm-${clustering_model_type}-k1-${num_clusters1}-k2-${num_clusters2}-q-${num_queries}.txt
      if [[ ! -e $cluster_file_path ]]; then
        bash /root/qbatcher/scripts/run-cost-model-yarn.sh ${dataset} ${training_file_path} ${test_file_path} 0 ${cluster_file_path} ${cost_model_type} 20 ${clustering_model_type} ${num_clusters1} ${num_clusters2} ${feature_columns_for_query_cost} ${feature_columns_for_clustering1} ${feature_columns_for_clustering2} ${label_col_for_for_query_cost} ${num_queries} ${training_file_path_for_batch_scheduler} ${regression_model_type_for_batch_scheduler} ${feature_cols_for_batch_scheduler} ${num_iter_for_batch_scheduler} ${num_batch_col1} ${num_batch_col2} ${label_col_for_batch_scheduler} > ${cluster_file_path}.log
      fi
      echo "done with run-cost-model-yarn.sh"
      declare -a args=()
      bash /root/qbatcher/scripts/clear-tmp-files.sh # > /dev/null 2&>1
      bash /root/qbatcher/scripts/run-gen-partitions.sh ${dataset} ${num_rows} ${num_partitions}
      batch_count=0
      ptime="0.0"
      for batch in $(cat ${cluster_file_path}); do
        qbatcher_count=0
        qbatcher_sum="0.0"
        qbatcher_avg="0.0"
        for (( i=0; i<${num_iter}; i++ )); do
          tmp_file=${output_dir}/res-qbatcher-d-${dataset}-q-${num_queries}-k1-${num_clusters1}-k2-${num_clusters2}-i-${i}-b-${batch_count}
          time=$(cat ${tmp_file})
          if [[ ${time:0:1} =~ [0-9] ]] && [[ ${time} != "0.0" ]]; then
            echo "pass with ${dataset} ${num_queries} ${num_clusters1} ${num_clusters2} ${i} ${batch_count}"
            qbatcher_sum=$(python -c "print(round(${qbatcher_sum} + ${time}, 1))")
            (( qbatcher_count++ ))
            continue
          fi
          echo "start with ${dataset} ${num_queries} ${num_clusters1} ${num_clusters2} ${i} ${batch_count}"
          result_file=${tmp_file}.log
          {
            bash /root/qbatcher/scripts/run-qaad-yarn.sh ${dataset} ${num_rows} ${num_partitions} ${num_queries} ${batch} 0 > ${result_file};
            time=$(bash /root/qbatcher/scripts/get-job-time.sh)
            echo $time > ${tmp_file}
          } &
          spark_proc_id=$!
          {
            sleep ${duration}m; echo "timeout" > ${tmp_file}
          } &
          sleep_proc_id=$!
          wait -n ${spark_proc_id} ${sleep_proc_id}
          result=$(cat ${tmp_file})
          if [[ ${result} == "timeout" ]]; then
            kill ${spark_proc_id}
            yarn_app_id=$(grep -oP "application_\d{13}_\d{4}" ${result_file} | head -1)
            if [ -n "$yarn_app_id" ]; then
              yarn application -kill "$yarn_app_id" > /dev/null
            fi
            bash /root/qbatcher/scripts/clear-tmp-files.sh # > /dev/null 2&>1
            bash /root/qbatcher/scripts/run-gen-partitions.sh ${dataset} ${num_rows} ${num_partitions}
            (( i-- ))
            continue
          else
            time=$(cat ${tmp_file})
            if [[ ${time:0:1} =~ [0-9] ]] && [[ ${time} != "0.0" ]]; then
              qbatcher_sum=$(python -c "print(round(${qbatcher_sum} + ${time}, 1))")
            else
              bash /root/qbatcher/scripts/clear-tmp-files.sh # > /dev/null 2&>1
              bash /root/qbatcher/scripts/run-gen-partitions.sh ${dataset} ${num_rows} ${num_partitions}
              (( i-- ))
              continue
            fi
            (( qbatcher_count++ ))
            kill ${sleep_proc_id}
            bash /root/qbatcher/scripts/clear-tmp-files.sh > /dev/null 2&>1
            bash /root/qbatcher/scripts/run-gen-partitions.sh ${dataset} ${num_rows} ${num_partitions}
          fi
        done
        if [[ $qbatcher_count -gt 0 ]]; then
          qbatcher_avg=$(python -c "print(round(${qbatcher_sum} / ${qbatcher_count}, 1))")
        else
          qbatcher_avg="0.0"
        fi
        # get the number of elements of a comma-seperated string (batch)
        ptime=$(python -c "print(round(${ptime} + ${qbatcher_avg}, 1))")
        num_queries_per_batch=$(grep -o "," <<< "${batch}" | wc -l)
        (( num_queries_per_batch++ ))
        args+=("${num_queries_per_batch},${qbatcher_avg} ")
        (( batch_count++ ))
      done
      qbatcher_response_time=$(python3 /root/qbatcher/scripts/get-response-time.py opt ${args[@]})
      echo "${qbatcher_response_time}" > ${output_dir}/queryset-${dataset}-responsetime-r-${num_rows}-p-${num_partitions}-q-${num_queries}-k1-${num_clusters1}-k2-${num_clusters2}-qbatcher.dat
      rm -rf ${cluster_file_path} ${output_dir}/*log
    done
  done
done

reset
