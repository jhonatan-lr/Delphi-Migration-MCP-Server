unit uPedidoVenda;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, DBGrids, DB, ADODB, ComCtrls, ExtCtrls, Buttons,
  DateTimePicker, Mask;

type
  TfrmPedidoVenda = class(TForm)
    edNumero: TEdit;
    edCliente: TLabeledEdit;
    edpDataPedido: TDateTimePicker;
    cmbStatus: TComboBox;
    memObservacao: TMemo;
    grdItens: TDBGrid;
    edTotal: TEdit;
    edDesconto: TEdit;
    btnNovo: TBitBtn;
    btnSalvar: TBitBtn;
    btnCancelar: TBitBtn;
    btnImprimir: TBitBtn;
    btnFechar: TBitBtn;
    pnlTopo: TPanel;
    pnlGrid: TPanel;
    pnlRodape: TPanel;
    pgcAbas: TPageControl;
    tsItens: TTabSheet;
    tsObservacoes: TTabSheet;
    qryPedidos: TADOQuery;
    qryItens: TADOQuery;
    dsPedidos: TDataSource;
    dsItens: TDataSource;
    procedure FormCreate(Sender: TObject);
    procedure FormDestroy(Sender: TObject);
    procedure btnSalvarClick(Sender: TObject);
    procedure btnNovoClick(Sender: TObject);
    procedure btnCancelarClick(Sender: TObject);
    procedure btnImprimirClick(Sender: TObject);
    procedure btnFecharClick(Sender: TObject);
    procedure edDescontoChange(Sender: TObject);
    procedure grdItensKeyDown(Sender: TObject; var Key: Word; Shift: TShiftState);
  private
    FPedidoId: Integer;
    FModoEdicao: Boolean;
    FTotalBruto: Currency;
    FDesconto: Currency;
    FTotalLiquido: Currency;
    procedure CarregarPedido(AId: Integer);
    procedure CarregarItens(APedidoId: Integer);
    procedure ValidarPedido;
    procedure CalcularTotais;
    procedure LimparFormulario;
    procedure HabilitarEdicao(AHabilitar: Boolean);
  public
    procedure AbrirNovo;
    procedure AbrirParaEdicao(AId: Integer);
    function ObterTotalLiquido: Currency;
  end;

var
  frmPedidoVenda: TfrmPedidoVenda;

implementation

{$R *.dfm}

procedure TfrmPedidoVenda.FormCreate(Sender: TObject);
begin
  FPedidoId    := 0;
  FModoEdicao  := False;
  FTotalBruto  := 0;
  FDesconto    := 0;
  FTotalLiquido := 0;
  LimparFormulario;
  HabilitarEdicao(False);
end;

procedure TfrmPedidoVenda.FormDestroy(Sender: TObject);
begin
  qryPedidos.Close;
  qryItens.Close;
end;

procedure TfrmPedidoVenda.CarregarPedido(AId: Integer);
begin
  qryPedidos.Close;
  qryPedidos.SQL.Text :=
    'SELECT P.ID, P.NUMERO, P.ID_CLIENTE, C.NOME AS NOME_CLIENTE, ' +
    '       P.DATA_PEDIDO, P.STATUS, P.OBSERVACAO, ' +
    '       P.TOTAL_BRUTO, P.DESCONTO, P.TOTAL_LIQUIDO ' +
    'FROM PEDIDOS P ' +
    'INNER JOIN CLIENTES C ON C.ID = P.ID_CLIENTE ' +
    'WHERE P.ID = :ID AND P.ATIVO = 1';
  qryPedidos.Parameters.ParamByName('ID').Value := AId;
  qryPedidos.Open;

  if not qryPedidos.IsEmpty then
  begin
    FPedidoId         := qryPedidos.FieldByName('ID').AsInteger;
    edNumero.Text     := qryPedidos.FieldByName('NUMERO').AsString;
    edCliente.Text    := qryPedidos.FieldByName('NOME_CLIENTE').AsString;
    FTotalBruto       := qryPedidos.FieldByName('TOTAL_BRUTO').AsCurrency;
    FDesconto         := qryPedidos.FieldByName('DESCONTO').AsCurrency;
    FTotalLiquido     := qryPedidos.FieldByName('TOTAL_LIQUIDO').AsCurrency;
    edTotal.Text      := FormatCurr('###,##0.00', FTotalLiquido);
  end;

  CarregarItens(FPedidoId);
end;

procedure TfrmPedidoVenda.CarregarItens(APedidoId: Integer);
begin
  qryItens.Close;
  qryItens.SQL.Text :=
    'SELECT I.ID, I.SEQUENCIA, P.CODIGO AS COD_PRODUTO, P.DESCRICAO, ' +
    '       I.QUANTIDADE, I.VALOR_UNITARIO, ' +
    '       (I.QUANTIDADE * I.VALOR_UNITARIO) AS SUBTOTAL ' +
    'FROM PEDIDO_ITENS I ' +
    'INNER JOIN PRODUTOS P ON P.ID = I.ID_PRODUTO ' +
    'WHERE I.ID_PEDIDO = :ID_PEDIDO ' +
    'ORDER BY I.SEQUENCIA';
  qryItens.Parameters.ParamByName('ID_PEDIDO').Value := APedidoId;
  qryItens.Open;
end;

procedure TfrmPedidoVenda.ValidarPedido;
begin
  if edCliente.Text = '' then
  begin
    ShowMessage('Informe o cliente do pedido!');
    edCliente.SetFocus;
    Abort;
  end;

  if edpDataPedido.DateTime < Now - 30 then
  begin
    ShowMessage('Data do pedido não pode ser anterior a 30 dias!');
    edpDataPedido.SetFocus;
    Abort;
  end;

  if qryItens.IsEmpty then
  begin
    ShowMessage('O pedido deve ter pelo menos um item!');
    Abort;
  end;

  if FTotalLiquido <= 0 then
    raise Exception.Create('Total do pedido inválido. Verifique os itens e desconto.');

  if FDesconto > FTotalBruto * 0.3 then
  begin
    if MessageDlg('Desconto acima de 30%. Confirma?', mtConfirmation, [mbYes, mbNo], 0) = mrNo then
      Abort;
  end;
end;

procedure TfrmPedidoVenda.CalcularTotais;
var
  vDesconto: Currency;
begin
  vDesconto := StrToCurrDef(edDesconto.Text, 0);
  if vDesconto < 0 then
  begin
    ShowMessage('Desconto não pode ser negativo!');
    edDesconto.Text := '0,00';
    Exit;
  end;
  FDesconto     := vDesconto;
  FTotalLiquido := FTotalBruto - FDesconto;
  edTotal.Text  := FormatCurr('###,##0.00', FTotalLiquido);
end;

procedure TfrmPedidoVenda.btnSalvarClick(Sender: TObject);
begin
  ValidarPedido;

  if FPedidoId = 0 then
  begin
    // Inserção
    qryPedidos.SQL.Text :=
      'INSERT INTO PEDIDOS (ID_CLIENTE, DATA_PEDIDO, STATUS, OBSERVACAO, TOTAL_BRUTO, DESCONTO, TOTAL_LIQUIDO, ATIVO) ' +
      'VALUES (:ID_CLIENTE, :DATA_PEDIDO, :STATUS, :OBS, :TOTAL_BRUTO, :DESCONTO, :TOTAL_LIQUIDO, 1)';
    qryPedidos.Parameters.ParamByName('STATUS').Value := 'ABERTO';
  end
  else
  begin
    // Atualização
    qryPedidos.SQL.Text :=
      'UPDATE PEDIDOS SET STATUS = :STATUS, OBSERVACAO = :OBS, ' +
      'DESCONTO = :DESCONTO, TOTAL_LIQUIDO = :TOTAL_LIQUIDO ' +
      'WHERE ID = :ID';
    qryPedidos.Parameters.ParamByName('ID').Value := FPedidoId;
  end;
  qryPedidos.ExecSQL;
  ShowMessage('Pedido salvo com sucesso!');
  HabilitarEdicao(False);
end;

procedure TfrmPedidoVenda.btnNovoClick(Sender: TObject);
begin
  LimparFormulario;
  FModoEdicao := True;
  HabilitarEdicao(True);
  edCliente.SetFocus;
end;

procedure TfrmPedidoVenda.btnCancelarClick(Sender: TObject);
begin
  if FPedidoId > 0 then
    CarregarPedido(FPedidoId)
  else
    LimparFormulario;
  FModoEdicao := False;
  HabilitarEdicao(False);
end;

procedure TfrmPedidoVenda.btnImprimirClick(Sender: TObject);
begin
  if FPedidoId <= 0 then
  begin
    ShowMessage('Salve o pedido antes de imprimir.');
    Exit;
  end;
  // TODO: Chamar relatório QuickReport/FastReport
end;

procedure TfrmPedidoVenda.btnFecharClick(Sender: TObject);
begin
  Close;
end;

procedure TfrmPedidoVenda.edDescontoChange(Sender: TObject);
begin
  CalcularTotais;
end;

procedure TfrmPedidoVenda.LimparFormulario;
begin
  edNumero.Text     := '';
  edCliente.Text    := '';
  edDesconto.Text   := '0,00';
  edTotal.Text      := '0,00';
  memObservacao.Clear;
  FPedidoId    := 0;
  FTotalBruto  := 0;
  FDesconto    := 0;
  FTotalLiquido := 0;
end;

procedure TfrmPedidoVenda.HabilitarEdicao(AHabilitar: Boolean);
begin
  edCliente.Enabled        := AHabilitar;
  edpDataPedido.Enabled    := AHabilitar;
  cmbStatus.Enabled        := AHabilitar;
  edDesconto.Enabled       := AHabilitar;
  memObservacao.ReadOnly   := not AHabilitar;
  btnSalvar.Enabled        := AHabilitar;
  btnCancelar.Enabled      := AHabilitar;
  btnNovo.Enabled          := not AHabilitar;
  btnImprimir.Enabled      := not AHabilitar and (FPedidoId > 0);
end;

procedure TfrmPedidoVenda.AbrirNovo;
begin
  btnNovoClick(nil);
end;

procedure TfrmPedidoVenda.AbrirParaEdicao(AId: Integer);
begin
  CarregarPedido(AId);
  FModoEdicao := True;
  HabilitarEdicao(True);
end;

function TfrmPedidoVenda.ObterTotalLiquido: Currency;
begin
  Result := FTotalLiquido;
end;

procedure TfrmPedidoVenda.grdItensKeyDown(Sender: TObject; var Key: Word;
  Shift: TShiftState);
begin
  if Key = VK_DELETE then
  begin
    if MessageDlg('Remover item selecionado?', mtConfirmation, [mbYes, mbNo], 0) = mrYes then
    begin
      qryItens.SQL.Text := 'DELETE FROM PEDIDO_ITENS WHERE ID = :ID';
      qryItens.Parameters.ParamByName('ID').Value := qryItens.FieldByName('ID').AsInteger;
      qryItens.ExecSQL;
      CarregarItens(FPedidoId);
      CalcularTotais;
    end;
  end;
end;

end.
