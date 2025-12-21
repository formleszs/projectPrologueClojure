find_path(StartX, StartY, EndX, EndY, Path) :-
    bfs([[(StartX, StartY)]], (EndX, EndY), [(StartX, StartY)], RevPath),
    reverse(RevPath, Path).

bfs([], _, _, _) :-
    fail.

bfs([[Goal|Rest]|_], Goal, _, [Goal|Rest]) :- !.

bfs([[(X,Y)|RestPath] | OtherPaths], Goal, Visited, FoundPath) :-
    findall([(NX,NY),(X,Y)|RestPath],
            ( adjacent((X,Y),(NX,NY)),
              valid_move(NX, NY),
              \+ member((NX,NY), Visited)
            ),
            NewPaths),
    new_cells_only(NewPaths, NewCells),
    append(Visited, NewCells, Visited1),
    append(OtherPaths, NewPaths, Queue1),
    bfs(Queue1, Goal, Visited1, FoundPath).

new_cells_only([], []).
new_cells_only([ [Cell|_] | Paths], [Cell|Cells]) :-
    new_cells_only(Paths, Cells).

adjacent((X,Y), (X1,Y)) :- X1 is X + 1.
adjacent((X,Y), (X1,Y)) :- X1 is X - 1.
adjacent((X,Y), (X,Y1)) :- Y1 is Y + 1.
adjacent((X,Y), (X,Y1)) :- Y1 is Y - 1.

find_nearest_player(BotX, BotY, PlayerX, PlayerY) :-
    findall(Distance-PlayerId-PX-PY,
            ( player_position(PlayerId, PX, PY),
              distance(BotX, BotY, PX, PY, Distance)
            ),
            Distances),
    sort(Distances, [_-_-PlayerX-PlayerY|_]).

distance(X1, Y1, X2, Y2, D) :-
    DX is X2 - X1,
    DY is Y2 - Y1,
    D is sqrt(DX*DX + DY*DY).