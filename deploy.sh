export GTS_HOME=/home/ubuntu/opengts

$GTS_HOME/bin/runserver.pl -s tk10x -kill
$GTS_HOME/bin/runserver.pl -s coban -kill

$GTS_HOME/bin/runserver.sh -s tk10x -p 22001
$GTS_HOME/bin/runserver.sh -s coban -p 22002
