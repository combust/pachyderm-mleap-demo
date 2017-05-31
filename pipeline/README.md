# Pachyderm demo steps

The following steps could be performed on any Pachyderm cluster using the `pachctl` CLI.  An easy way to test is with the local installation detailed here: http://docs.pachyderm.io/en/latest/getting_started/local_installation.html.

```
# create the training repo
$ pachctl create-repo training

# create the test repo
$ pachctl create-repo test

# confirm the creation of the repos
$ pachctl list-repo

# push the training data into pachyderm
$ pachctl put-file training master -c -f lending_club.avro

# confirm that the training data is there
$ pachctl list-repo
$ pachctl list-file training master

# create the training pipeline
$ pachctl create-pipeline -f train.json

# observe and wait for the training job to finish
$ pachctl list-job

# observe and confirm the output of the training
$ pachctl list-repo
$ pachctl list-file model master

# create the scoring pipeline
$ pachctl create-pipeline -f score.json

# put the test data set into pachyderm
$ pachctl put-file test master -c -f lending_club.avro

# observe and confirm the scoring job
$ pachctl list-job

# observe and confirm the output of the scoring
$ pachctl list-repo
$ pachctl list-file scoring master 
```

