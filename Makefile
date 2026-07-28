.PHONY: run demo privacy contest batch test clean

run:
	./scripts/run.sh

demo:
	./scripts/demo.sh demo

privacy:
	./scripts/demo.sh privacy

contest:
	./scripts/demo.sh contest

batch:
	./scripts/batch.sh

test:
	./scripts/test.sh

clean:
	rm -rf build
