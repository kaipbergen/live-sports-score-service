.PHONY: build run test package docker-up docker-down demo clean

build:
	mvn -q -DskipTests package

run:
	mvn spring-boot:run

test:
	mvn test

package:
	mvn package

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down

demo:
	./scripts/demo.sh

clean:
	mvn clean
