import sys

def main():
    for line in sys.stdin.readlines():
        items = [s.strip() for s in line.split('|')][1:]
        model = items[0]
        api_levels = items[5]

        for api in api_levels.split(','):
            print('  --device model={},version={},locale=en,orientation=portrait \\'.format(model, api))

if __name__ == '__main__':
    main()
