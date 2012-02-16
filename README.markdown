# scruffian

Provides a REST-like API for uploading and downloading files to and from iRODS.

## Downloading

    curl 'http://127.0.0.1:31370/download?user=testuser&path=/iplant/home/testuser/myfile.txt'
    
This will result is the file contents being barfed out to stdout. Redirect to a file to actually get the file

Errors will result in either a 400, 404, or 500 HTTP status and either a stacktrace or one of these messages (soon to become JSON responses):

    File <file-path> not found. (used with a 404)

    File <file-path> is not readable. (used with a 400)

## Uploading

Uploading is handled through multipart requests. Here's a curl command:

    curl -F file=@testfile.txt -F user=testuser -F dest=/iplant/home/testuser/ http://127.0.0.1:31370/upload
    
The username is provided in the form data rather than as a query parameter.
Also notice that the 'dest' value points to a directory and not a file.

A success will return JSON like this:

    {
        "action" : "file-upload",
        "status" : "success",
        "file" : {
            "id" : "<path to the file>",
            "label" : "<basename of the file path>",
            "permissions" : {
                "read" : true|false,
                "write" : true|false
            },
        "date-created" : "<seconds since the epoch as a string>",
        "date-modified" : "<seconds since the epoch as a string>",
        "file-size" : "<size in bytes as a string>"
    }

A failure looks like one of Nibblonians error JSON objects, with eiother a ERR_DOES_NOT_EXIST or ERR_NOT_WRITEABLE error_code and an action of "upload".

Example:

    {
        "status" : "failure",
        "error_code" : "ERR_DOES_NOT_EXIST",
        "id" : "<intended file path>",
        "action" : "upload"
    }

## URL Uploads

It's easiest to show this through a curl command.

    curl -H "Content-Type:application/json" -d '{"dest" : "/iplant/home/testuser/testfile.txt", "address" : "http://www.google.com"}' http://127.0.0.1:31370/urlupload?user=testuser
    
Adding the "Content-Type" header is required. You'll get JSON parsing or format errors otherwise.
The 'dest' value in the JSON refers to the full path, including the filename. That's the filename the contents of 'address' will be saved off to. The file will be overwritten if it already exists.

On success you should get JSON that looks like this:

    {
        "status" : "success",
        "action" : "url-upload",
        "msg" : "Upload scheduled.",
        "url" : "<URL>",
        "label" : "<URL base filename>",
        "dest" : "<destination in irods>"
    }
    
On on error, you'll either get a stacktrace or JSON that looks like this:

    {
        "status" : "failure",
        "action" : "url-upload",
        "msg" : "<JSON passed in through the request>",
        "error_code" : "ERR_REQUEST_FAILED"
    }

## Note on Uploads and URL Uploads

Uploads and URL uploads are both staged in a temporary directory in iRODS before being moved to their final location.

