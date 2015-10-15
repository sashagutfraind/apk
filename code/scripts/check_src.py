#!/usr/bin/python
import os

search_dirs = ['.', '../src', '../APK.rs', '../batch']

strings_critical = ['FIX'+'ME', 'fix'+'me']
strings_optional = [] #['TODO', 'todo', 'wishlist']
search_pattern = 'grep -r "%s" %s --color=always -n | grep -v "svn" -'
#-v inverts the matching

print "Critical"
print "--------"
for d in search_dirs:
    print 'Dir: ' + d
    for s in strings_critical:
        os.system(search_pattern%(s, d))

print "Optional"
print "--------"
for d in search_dirs:
    print 'Dir: ' + d
    for s in strings_optional:
        os.system(search_pattern%(s, d))
