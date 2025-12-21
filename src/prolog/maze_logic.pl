% Определение ячейки
cell(X, Y, empty) :- \+ wall(X, Y).
cell(X, Y, wall) :- wall(X, Y).

% Проверка допустимости хода
valid_move(X, Y) :-
    cell(X, Y, empty),
    within_bounds(X, Y).

within_bounds(X, Y) :-
    maze_size(Width, Height),
    X >= 0, X < Width,
    Y >= 0, Y < Height.

% Лабиринт
wall(0, 0).             wall(2, 0). wall(3, 0). wall(4, 0).
wall(0, 1).                                     wall(4, 1).
wall(0, 2). wall(1, 2). wall(2, 2).             wall(4, 2).
wall(0, 3).                                     wall(4, 3).
wall(0, 4).             wall(2, 4).             wall(4, 4).
wall(0, 5).             wall(2, 5).             wall(4, 5).
wall(0, 6). wall(1, 6). wall(2, 6). wall(3, 6). wall(4, 6).

maze_size(5, 7).

exit(1, 0).