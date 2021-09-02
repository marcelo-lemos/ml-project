import argparse
from functools import wraps
import logging
import logging.config
import multiprocessing
import os
import pathlib
import random
from typing import Text

TEST_ONLY = False

SCRIPT_PATH = pathlib.Path(__file__).resolve().parents[1] / 'rlexperiment.sh'
CONFIG_PATH = pathlib.Path(__file__).resolve().parents[1] / 'experiments'

DEFAULT_EXPERIMENTS = ['specific', 'nemesis']
DEFAULT_ADVERSARIES = [
    'AHTN',
    'NaiveMCTS',
    'PuppetAB',
    'PuppetMCTS',
    'StrategyTactics'
]
DEFAULT_ITERATIONS = 5
DEFAULT_OUTPUT = pathlib.Path(__file__).resolve().parents[1] / 'experiment-results'

def async_function(function):
    """Decorator for async functions."""
    @wraps(function)
    def wrapper(*args, **kwargs):
        process = multiprocessing.Process(
            target=function, args=args, kwargs=kwargs)
        process.start()
        return process
    return wrapper


def create_dirs(path):
    try:
        os.makedirs(path)
    except FileExistsError:
        # directory already exists
        return


# TODO: improve function
def generate_call(script, p1_seed=None, p2_seed=None,
                  p1_bin_in=None, p2_bin_in=None,
                  p1_bin_out=None, p2_bin_out=None,
                  p1_dir=None, p2_dir=None,
                  sticky_dur=None,
                  config=None, output_path=None, log_path=None):
    call = [f'{script}']
    if config is not None:
        call.append(f'-c {config}')
    if p1_seed is not None:
        call.append(f'-s1 {p1_seed}')
    if p2_seed is not None:
        call.append(f'-s2 {p2_seed}')
    if p1_bin_in:
        call.append(f'-bi1 {p1_bin_in}')
    if p2_bin_in:
        call.append(f'-bi2 {p2_bin_in}')
    if p1_bin_out:
        call.append('-b1')
    if p2_bin_out:
        call.append('-b2')
    if p1_dir is not None:
        call.append(f'-d1 {p1_dir}')
    if p2_dir is not None:
        call.append(f'-d2 {p2_dir}')
    if sticky_dur is not None:
        call.append(f'-sd {sticky_dur}')
    if output_path is not None:
        call.append(f'-o {output_path}')
    if log_path is not None:
        call.append(f'> {log_path}')
    return ' '.join(call)


@async_function
def specific_experiment(adversary, sticky, seed, output):
    logger = logging.getLogger(f'Specific - {adversary} {seed}')

    working_dir = f'{output}/{adversary}/sticky_{sticky}/rep_{seed}'
    create_dirs(f'{working_dir}')

    p2_dir = f'{working_dir}'

    if not TEST_ONLY:
        # Training
        config = f'{CONFIG_PATH}/specific/{adversary}/train.properties'
        output_path = f'{p2_dir}/train-results.txt'
        log = f'{p2_dir}/train.log'

        logger.info("Starting training.")
        os_train_call = generate_call(
            SCRIPT_PATH,
            config=config,
            p2_seed=random.randint(0, 1000000),
            p2_bin_out=True,
            p2_dir=p2_dir,
            sticky_dur=sticky,
            output_path=output_path,
            log_path=log)
        os.system(os_train_call)
        # print(os_train_call)

    # Testing
    config = f'{CONFIG_PATH}/specific/{adversary}/test.properties'
    p2_bin_in = f'{p2_dir}/weights_1.bin'
    output_path = f'{p2_dir}/test-results.txt'
    log = f'{p2_dir}/test.log'

    logger.info("Starting testing.")
    os_test_call = generate_call(
        SCRIPT_PATH,
        config=config,
        p2_seed=random.randint(0, 1000000),
        p2_bin_in=p2_bin_in,
        p2_dir=p2_dir,
        sticky_dur=sticky,
        output_path=output_path,
        log_path=log)
    os.system(os_test_call)
    # print(os_test_call)

    logger.info('Experiment done.')


def multithreading_experiments(adversaries, sticky, iterations, output):
    # TODO: Add logging messages

    for adversary in adversaries:
        threads = []

        for i in range(iterations):
            thread = specific_experiment(adversary, sticky, i, output)
            threads.append(thread)

        for thread in threads:
            thread.join()


def ijcai_experiments(adversary, sticky_durations, iterations, output):
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s - %(levelname)s - %(name)s - %(message)s')
    logger = logging.getLogger('Experiments')

    # Check if number of iterations is valid
    if iterations <= 0:
        logger.error("Invalid int value: %d", iterations)
        return

    logger.info("Launching experiments.")
    for duration in sticky_durations:
        multithreading_experiments(adversary, duration, iterations, output)

    logger.info('Experiments done.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-a', '--adversary', nargs='+')
    parser.add_argument('-s', '--sticky', nargs='+')
    parser.add_argument('-i', '--iterations', type=int, default=5)
    parser.add_argument('-o', '--output', default=DEFAULT_OUTPUT)
    parser.add_argument('-t', '--test-only', action='store_true')
    cmd_args = parser.parse_args()

    TEST_ONLY = cmd_args.test_only

    ijcai_experiments(
        cmd_args.adversary,
        cmd_args.sticky,
        cmd_args.iterations,
        cmd_args.output)
