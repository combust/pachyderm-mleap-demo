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

```
docker run -v /Users/hollinwilkins/Workspace/scratch/data:/data-in \
  -v /tmp/test:/data-out combustml/pmd-training:0.1-SNAPSHOT airbnb \
  -t random-forest \ # train a random forest model
  -i file:///data-in/airbnb.clean.avro \ # input airbnb dataset
  -o /data-out/model.zip \ # set the output location of the model file
  -J-Xmx2048m # make sure Spark has enough memory
```

## Scoring

TODO: documentation for how to run the scoring image

