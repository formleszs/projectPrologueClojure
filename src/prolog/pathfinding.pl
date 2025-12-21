find_path(StartX, StartY, EndX, EndY, Path) :-
    find_path_bfs([(StartX, StartY)], EndX, EndY, [(StartX, StartY)], Path).

find_path_bfs([], _, _, _, _) :- 
    fail.

find_path_bfs([(EndX, EndY)|_], EndX, EndY, Visited, Path) :-
    reverse(Visited, Path).

find_path_bfs([(X,Y)|Rest], EndX, EndY, Visited, Path) :-
    findall((NX, NY), 
            (adjacent(X, Y, NX, NY), 
             valid_move(NX, NY),
             \+ member((NX, NY), Visited)),
            NewCells),
    append(Rest, NewCells, NewQueue),
    append(Visited, NewCells, NewVisited),
    find_path_bfs(NewQueue, EndX, EndY, NewVisited, Path).

adjacent(X, Y, X1, Y) :- X1 is X + 1.
adjacent(X, Y, X1, Y) :- X1 is X - 1.
adjacent(X, Y, X, Y1) :- Y1 is Y + 1.
adjacent(X, Y, X, Y1) :- Y1 is Y - 1.

% Найти ближайшего игрока для бота
find_nearest_player(BotX, BotY, PlayerX, PlayerY) :-
    player_position(PlayerId, PlayerX, PlayerY),
    findall(Distance-PlayerId-PX-PY,
            (player_position(PId, PX, PY),
             distance(BotX, BotY, PX, PY, Distance)),
            Distances),
    sort(Distances, [_-PlayerId-PlayerX-PlayerY|_]).

distance(X1, Y1, X2, Y2, D) :-
    DX is X2 - X1,
    DY is Y2 - Y1,
    D is sqrt(DX*DX + DY*DY).