#!/bin/sh
grep -ERno "\-\-\-.*|FIXME.*|TODO.*" doc project.clj src src-java test
