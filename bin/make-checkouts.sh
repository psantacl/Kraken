test -d checkouts || mkdir checkouts

function make_checkouts_link () {
  INP=$1
  if [ -d "$INP" ]; then
    ln -s "$INP" "$(dirname $0)/../checkouts/"
  else
    echo "Error: $INP does not exist (can't symlink it to ./checkouts)."
    exit 1
  fi
}

make_checkouts_link $HOME/development/aleph

