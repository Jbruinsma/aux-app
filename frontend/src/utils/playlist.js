function shuffleArray(array) {
  let currentIndex = array.length;
  while (currentIndex > 1) {
    const randomIndex = Math.floor(Math.random() * currentIndex);
    currentIndex--;
    const temp = array[currentIndex];
    array[currentIndex] = array[randomIndex];
    array[randomIndex] = temp;
  }
}

export function shufflePlaylist(playlist, startIndex = 0){
  const selectedPiece = playlist[startIndex];
  const remainingPieces = playlist.slice(0, startIndex).concat(playlist.slice(startIndex + 1));
  shuffleArray(remainingPieces);
  return [selectedPiece, ...remainingPieces]
}
