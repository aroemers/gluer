#!/bin/sh
grep -ERno --color=auto "\-\-\-.*|FIXME.*|TODO.*" doc project.clj src src-java test
