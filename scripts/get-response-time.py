import sys
from itertools import permutations

def greedy_order(pairs):
    # Sort pairs based on the time taken, and return their indices in sorted order.
    return [i[0] for i in sorted(enumerate(pairs), key=lambda x: -float(x[1][0]) / x[1][1])]

def greedy_order_sjf(pairs):
    # Sort pairs based on the time taken, and return their indices in sorted order.
    return [i[0] for i in sorted(enumerate(pairs), key=lambda x: -x[1][1])]


def compute_avg_time(order, pairs):
    total_time = 0.0
    total_queries = 0
    acc_time = 0.0
    for idx, current in enumerate(order):
        n, t = pairs[current]
        total_time += n * (t + acc_time)
        acc_time += t
        total_queries += n
    return total_time / total_queries

def optimal_avg_time(pairs):
    indices = range(len(pairs))
    min_avg_time = float('inf')
    best_order = None

    for order in permutations(indices):
        avg_time = compute_avg_time(order, pairs)
        if avg_time < min_avg_time:
            min_avg_time = avg_time
            best_order = order

    return min_avg_time, best_order

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Please provide pairs in the format n,t where t can be a float.")
        sys.exit(1)

    method = sys.argv[1]
    pairs = [(int(n), float(t)) for n, t in (arg.split(',') for arg in sys.argv[2:])]
    
    if method == 'sjf':
        best_order = greedy_order_sjf(pairs)
        min_avg_time = compute_avg_time(best_order, pairs)
    elif len(pairs) > 10:
        best_order = greedy_order(pairs)
        min_avg_time = compute_avg_time(best_order, pairs)
    else:
        min_avg_time, best_order = optimal_avg_time(pairs)

    print(f"{min_avg_time}")

