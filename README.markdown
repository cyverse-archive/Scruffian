# scruffian

Provides a REST-like API for uploading and downloading files to and from iRODS.

## Error handling

If you try to hit an endpoint that doesn't exist, you'll get a 404.

For all other errors, you should receive a 500 HTTP status code and a JSON body in the following format:

    {
        "error_code" : "<Scruffian error code>",
        "status" : "failure",
        "action" : "<action name>
    }

Most errors will return other contextual fields, but they will vary from error to error. For programmatic usage, only depend on the three fields listed above.

Each section listed below lists the error codes that you may encounter. In addition to these, you may run into the ERR_UNCHECKED_EXCEPTION, which means that an uncaught exception was encountered.

## Downloading
Action: "file-download"

Error codes:

+ ERR_INVALID_JSON (wrong content-type or JSON syntax errors)
+ ERR_BAD_OR_MISSING_FIELD (JSON field is missing or has an invalid value)
+ ERR_MISSING_QUERY_PARAMETER (Query parameter is missing)
+ ERR_NOT_A_USER (invalid user specified)
+ ERR_DOES_NOT_EXIST (File request doesn't exist)
+ ERR_NOT_READABLE (File requested isn't readable by the specified user)

Curl command:

    curl 'http://127.0.0.1:31370/download?user=testuser&path=/iplant/home/testuser/myfile.txt'
    
This will result is the file contents being barfed out to stdout. Redirect to a file to actually get the file.

## Uploading
Action: "file-upload"

Error codes:

+ ERR_MISSING_FORM_FIELD (One of the form data fields is missing)
+ ERR_NOT_A_USER (Invalid user specified)
+ ERR_DOES_NOT_EXIST (Destination directory doesn't exist)
+ ERR_NOT_WRITEABLE (Destination directory isn't writeable)

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

## URL Uploads
Action: "url-upload"

Error codes:

+ ERR_INVALID_JSON (Missing content-type or JSON syntax error)
+ ERR_BAD_OR_MISSING_FIELD (Missing JSON field or invalid JSON field value)
+ ERR_MISSING_QUERY_PARAMETER (One of the query parameters is missing)
+ ERR_NOT_A_USER (Invalid user specified)
+ ERR_NOT_WRITEABLE (Destination directory isn't writeable by the specified user)
+ ERR_ERR_EXISTS (Destination file already exists)
+ ERR_REQUEST_FAILED (General failure to spawn upload thread)


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

## Note on Uploads

Uploads are staged in a temporary directory in iRODS before being moved to their final location.

## Save As
Action: "saveas"

Error codes:

+ ERR_INVALID_JSON (Missing content-type or JSON syntax error)
+ ERR_BAD_OR_MISSING_FIELD (Missing JSON field or invalid JSON field value)
+ ERR_MISSING_QUERY_PARAMETER (One of the query parameters is missing)
+ ERR_NOT_A_USER (Invalid user specified)
+ ERR_DOES_NOT_EXIST (The destination directory does not exist)
+ ERR_NOT_WRITEABLE (The destination directory is not writable by the user)
+ ERR_EXISTS (The destination file already exists)


Curl:

    curl -H "Content-Type:application/json" -d '{"content" : "This is the content for the file.", "dest" : "/iplant/home/testuser/savedfile.txt"}' http://127.0.0.1:31370/saveas?user=wregglej
    
Success:

    {
        "action" : "saveas",
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
    
