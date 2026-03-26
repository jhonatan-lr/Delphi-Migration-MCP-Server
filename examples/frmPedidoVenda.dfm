object frmPedidoVenda: TfrmPedidoVenda
  Left = 200
  Top = 100
  Caption = 'Pedidos de Venda'
  ClientHeight = 600
  ClientWidth = 850
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'Tahoma'
  Font.Style = []
  object pnlTopo: TPanel
    Left = 0
    Top = 0
    Width = 850
    Height = 130
    Align = alTop
    object edNumero: TEdit
      Left = 120
      Top = 12
      Width = 80
      Height = 23
      ReadOnly = True
      TabOrder = 0
    end
    object edCliente: TLabeledEdit
      Left = 120
      Top = 42
      Width = 350
      Height = 23
      EditLabel.Caption = 'Cliente'
      TabOrder = 1
      OnChange = edClienteChange
    end
    object edpDataPedido: TDateTimePicker
      Left = 120
      Top = 72
      Width = 120
      Height = 23
      Date = 45000.0
      Time = 0.0
      TabOrder = 2
    end
    object cmbStatus: TComboBox
      Left = 280
      Top = 72
      Width = 130
      Height = 23
      Items.Strings = (
        'ABERTO'
        'CONFIRMADO'
        'FATURADO'
        'CANCELADO')
      TabOrder = 3
    end
    object edDesconto: TEdit
      Left = 650
      Top = 42
      Width = 100
      Height = 23
      Text = '0,00'
      TabOrder = 4
      OnChange = edDescontoChange
    end
    object edTotal: TEdit
      Left = 650
      Top = 72
      Width = 100
      Height = 23
      ReadOnly = True
      TabOrder = 5
    end
  end
  object pgcAbas: TPageControl
    Left = 0
    Top = 130
    Width = 850
    Height = 400
    Align = alClient
    object tsItens: TTabSheet
      Caption = 'Itens do Pedido'
      object grdItens: TDBGrid
        Left = 0
        Top = 0
        Width = 842
        Height = 365
        Align = alClient
        DataSource = dsItens
        Options = [dgTitles, dgIndicator, dgColumnResize, dgColLines, dgRowLines, dgTabs, dgRowSelect, dgAlwaysShowSelection, dgConfirmDelete]
        TabOrder = 0
        OnKeyDown = grdItensKeyDown
      end
    end
    object tsObservacoes: TTabSheet
      Caption = 'Observações'
      object memObservacao: TMemo
        Left = 0
        Top = 0
        Width = 842
        Height = 365
        Align = alClient
        TabOrder = 0
      end
    end
  end
  object pnlRodape: TPanel
    Left = 0
    Top = 530
    Width = 850
    Height = 50
    Align = alBottom
    object btnNovo: TBitBtn
      Left = 10
      Top = 10
      Width = 90
      Height = 30
      Caption = '&Novo'
      TabOrder = 0
      OnClick = btnNovoClick
    end
    object btnSalvar: TBitBtn
      Left = 110
      Top = 10
      Width = 90
      Height = 30
      Caption = '&Salvar'
      TabOrder = 1
      OnClick = btnSalvarClick
    end
    object btnCancelar: TBitBtn
      Left = 210
      Top = 10
      Width = 90
      Height = 30
      Caption = '&Cancelar'
      TabOrder = 2
      OnClick = btnCancelarClick
    end
    object btnImprimir: TBitBtn
      Left = 640
      Top = 10
      Width = 90
      Height = 30
      Caption = '&Imprimir'
      TabOrder = 3
      OnClick = btnImprimirClick
    end
    object btnFechar: TBitBtn
      Left = 740
      Top = 10
      Width = 90
      Height = 30
      Caption = '&Fechar'
      TabOrder = 4
      OnClick = btnFecharClick
    end
  end
  object qryPedidos: TADOQuery
    ConnectionString = 'Provider=SQLOLEDB.1;...'
    Left = 20
    Top = 560
  end
  object qryItens: TADOQuery
    ConnectionString = 'Provider=SQLOLEDB.1;...'
    Left = 100
    Top = 560
  end
  object dsPedidos: TDataSource
    DataSet = qryPedidos
    Left = 180
    Top = 560
  end
  object dsItens: TDataSource
    DataSet = qryItens
    Left = 260
    Top = 560
  end
end
