game_won :-
    findall(Player, alive_player(Player), AlivePlayers),
    findall(Player, player_at_exit(Player), AtExitPlayers),
    length(AlivePlayers, AliveCount),
    length(AtExitPlayers, AtExitCount),
    AliveCount > 0,
    AliveCount =:= AtExitCount.

game_lost :-
    \+ alive_player(_).

player_at_exit(PlayerId) :-
    player_position(PlayerId, X, Y),
    exit(X, Y),
    alive_player(PlayerId).

alive_player(PlayerId) :-
    player_status(PlayerId, alive).