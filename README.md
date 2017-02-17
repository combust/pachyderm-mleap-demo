# Pachyderm/MLeap Demo

This is the codebase to support the Pachyderm/MLeap training and scoring
demo. It is used to generate the Docker images used by the demo.

## Docker Image

The Docker images are located here:

1. [Training Image](https://hub.docker.com/r/combustml/pmd-training/)
2. [Scoring Image](https://hub.docker.com/r/combustml/pmd-scoring/)

## Building Locally

Build the Docker image locally with SBT.

1. Install SBT with these [instructions](http://www.scala-sbt.org/0.13/docs/Setup.html)
2. Make sure docker is running
3. Use SBT to publish the image locally

```
sbt training/docker:publishLocal
sbt scoring/docker:publishLocal
```

This will publish two docker images named `combustml/pmd-training:0.1-SNAPSHOT` and
`combustml/pmd-scoring:0.1-SNAPSHOT`.

## Training

Download the Airbnb training dataset here: [airbnb.clean.avro](https://s3-us-west-2.amazonaws.com/mleap-demo/datasources/airbnb.clean.avro).

```
docker run -v /tmp/pmd-in:/data-in \
  -v /tmp/pmd-out:/data-training-out combustml/pmd-training:0.1-SNAPSHOT airbnb \
  -t random-forest \ # train a random forest model
  -i file:///data-in/airbnb.clean.avro \ # input airbnb dataset
  -o /data-out/model.zip \ # set the output location of the model file
  -s /data-out/summary.txt \ # output path for model summary
  -J-Xmx2048m # make sure Spark has enough memory
```

## Scoring

```
docker run -v /tmp/pmd-out:/data-in1 \
  -v /tmp/pmd-training-in:/data-int2 \
  -v /tmp/pmd-scoring-out:/data-out combustml/pmd-scoring:0.1-SNAPSHOT \
  -m /data-in1/model.zip \
  -i /data-in2/good.avro \
  -o /data-out/test-docker.avro \
  -J-Xmx2048m
```

