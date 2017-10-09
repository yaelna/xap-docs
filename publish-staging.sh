#!/bin/bash

set -e
export S3_ID=$S3_ACCESS_KEY_ID
export S3_SECRET=$S3_SECRET_KEY
export S3_BUCKET=docs.gigaspaces.com-v2
echo "Publishing staging to $S3_BUCKET..."
s3_website push