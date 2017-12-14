#!/bin/sh

[ -e "${JPF_HOME}" ] || JPF_HOME="${HOME}/.jpf/jpf-core"

${JPF_HOME}/bin/jpf  IntegerExample.jpf
#+listener=.listener.MethodSkipper