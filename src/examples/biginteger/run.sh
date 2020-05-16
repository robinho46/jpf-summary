#!/bin/sh

[ -e "${JPF_HOME}" ] || JPF_HOME="${HOME}/.jpf/jpf-core"

${JPF_HOME}/bin/jpf  BigInt.jpf
# to turn off summaries add
# +listener=.listener.SearchTimer
# a major cause of slowdown is the fact that transitions have a max length
# +listener+=,.listener.CGMonitor