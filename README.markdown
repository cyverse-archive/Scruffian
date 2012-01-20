# scruffian

Provides a REST-like API for uploading and downloading files to and from iRODS.

## Downloading

    curl 'http://127.0.0.1:31370/download?user=testuser&path=/iplant/home/testuser/myfile.txt'

## Uploading

Uploading is handled through multipart requests. Here's a curl command:

    curl -F file=@testfile.txt -F user=testuser -F dest=/iplant/home/testuser/ http://127.0.0.1:31370/upload
    
The username is provided in the form data rather than as a query parameter.
Also notice that the 'dest' value points to a directory and not a file.

## URL Uploads

It's easiest to show this through a curl command.

    curl -H "Content-Type:application/json" -d '{"dest" : "/iplant/home/testuser/testfile.txt", "address" : "http://www.google.com"}' http://127.0.0.1:31370/urlupload?user=testuser
    
Adding the "Content-Type" header is required. You'll get JSON parsing or format errors otherwise.
The 'dest' value in the JSON refers to the full path, including the filename. That's the filename the contents of 'address' will be saved off to. The file will be overwritten if it already exists.

## Note on Uploads and URL Uploads

Uploads and URL uploads are both staged in a temporary directory in iRODS before being moved to their final location.

